package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Duration;
import org.hl7.fhir.exceptions.FHIRException;
import org.openmrs.*;
import org.openmrs.Encounter;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.bahmniemrapi.drugorder.dosinginstructions.FlexibleDosingInstructions;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.*;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.OrderIdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;
import static org.openmrs.module.fhir.utils.FHIREncounterUtil.getIdPart;

@Component
public class FHIRMedicationRequestMapper implements FHIRResourceMapper {
    private static final int DEFAULT_NUM_REFILLS = 0;
    private final ObjectMapper objectMapper;

    private final OMRSConceptLookup omrsConceptLookup;
    private final ConceptService conceptService;
    private final OrderService orderService;
    private final FrequencyMapperUtil frequencyMapperUtil;
    private final DurationMapperUtil durationMapperUtil;
    private final ProviderLookupService providerLookupService;
    private final OrderCareSettingLookupService orderCareSettingLookupService;
    private final GlobalPropertyLookUpService globalPropertyLookUpService;
    private final IdMappingRepository idMappingRepository;

    private static final Logger logger = Logger.getLogger(FHIRMedicationRequestMapper.class);

    @Autowired
    public FHIRMedicationRequestMapper(OMRSConceptLookup omrsConceptLookup, ConceptService conceptService,
                                       OrderService orderService, FrequencyMapperUtil frequencyMapperUtil,
                                       DurationMapperUtil durationMapperUtil, ProviderLookupService providerLookupService,
                                       OrderCareSettingLookupService orderCareSettingLookupService, GlobalPropertyLookUpService globalPropertyLookUpService,
                                       IdMappingRepository idMappingRepository) {
        objectMapper = new ObjectMapper();
        this.omrsConceptLookup = omrsConceptLookup;
        this.conceptService = conceptService;
        this.orderService = orderService;
        this.frequencyMapperUtil = frequencyMapperUtil;
        this.durationMapperUtil = durationMapperUtil;
        this.providerLookupService = providerLookupService;
        this.orderCareSettingLookupService = orderCareSettingLookupService;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
        this.idMappingRepository = idMappingRepository;
    }

    @Override
    public boolean canHandle(Resource resource) {
        return resource instanceof MedicationRequest;
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle encounterComposition, SystemProperties systemProperties) {
        MedicationRequest medicationRequest = (MedicationRequest) resource;
        if (!shouldSyncMedicationRequest(encounterComposition, medicationRequest)) return;
        DrugOrder drugOrder = createDrugOrder(encounterComposition, medicationRequest, emrEncounter, systemProperties);
        emrEncounter.addOrder(drugOrder);
    }

    private DrugOrder createDrugOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationRequest, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder drugOrder = new DrugOrder();
        Provenance provenance = FHIRBundleHelper.getProvenanceForResource(encounterComposition.getBundle(), medicationRequest.getId());
        DrugOrder previousDrugOrder = createOrFetchPreviousOrder(encounterComposition, medicationRequest, emrEncounter, systemProperties);
        if (previousDrugOrder != null) {
            drugOrder.setPreviousOrder(previousDrugOrder);
        }

        mapDrug(medicationRequest, drugOrder);
        if (medicationRequest.getDosageInstruction().isEmpty()) return null;
        //will work only because any order created through bahmni is activated immediately
        drugOrder.setDateActivated(medicationRequest.getAuthoredOn());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();
        mapFrequency(drugOrder, dosageInstruction);
        HashMap<String, Object> dosingInstructionsMap = new HashMap<>();
        addNotesAndInstructionsToDosingInstructions(medicationRequest, dosingInstructionsMap);
        setOrderDuration(drugOrder, dosageInstruction);
        if (dosageInstruction.getDose() != null) {
            if (((SimpleQuantity) dosageInstruction.getDose()).getValue() != null) {
                drugOrder.setDose(((SimpleQuantity) dosageInstruction.getDose()).getValue().doubleValue());
            } else {
                addCustomDosageToDosingInstructions(dosageInstruction, dosingInstructionsMap);
            }
            setDoseUnits(drugOrder, dosageInstruction);
        }
        setQuantity(drugOrder, medicationRequest.getDispenseRequest());
        setScheduledDateAndUrgency(drugOrder, provenance);
        setOrderAction(drugOrder, provenance);

        drugOrder.setRoute(mapRoute(dosageInstruction));
        drugOrder.setAsNeeded(((BooleanType) dosageInstruction.getAsNeeded()).getValue());
        drugOrder.setOrderer(getOrderer(medicationRequest));
        drugOrder.setNumRefills(DEFAULT_NUM_REFILLS);
        drugOrder.setCareSetting(orderCareSettingLookupService.getCareSetting());
        try {
            drugOrder.setDosingInstructions(objectMapper.writeValueAsString(dosingInstructionsMap));
        } catch (IOException e) {
            logger.warn("Unable to set dosageInstruction");
        }
        drugOrder.setDosingType(FlexibleDosingInstructions.class);

        addDrugOrderToIdMapping(drugOrder, medicationRequest, encounterComposition, systemProperties);
        return drugOrder;
    }

    private boolean shouldSyncMedicationRequest(ShrEncounterBundle encounterComposition, MedicationRequest medicationRequest) {
        return fetchOrderByExternalId(encounterComposition.getShrEncounterId(), getIdPart(medicationRequest.getId())) == null;
    }

    private OrderIdMapping fetchOrderByExternalId(String shrEncounterId, String medicationRequestId) {
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, medicationRequestId);
        return (OrderIdMapping) idMappingRepository.findByExternalId(externalId, IdMappingType.MEDICATION_ORDER);
    }

    private void setOrderAction(DrugOrder drugOrder, Provenance provenance) {
        Coding activity = provenance.getActivity();
        if (activity.isEmpty() || !FHIR_DATA_OPERATION_VALUESET_URL.equals(activity.getSystem())) {
            drugOrder.setAction(Order.Action.NEW);
            return;
        }
        if (FHIR_DATA_OPERATION_CREATE_CODE.equals(activity.getCode())) drugOrder.setAction(Order.Action.NEW);
        if (FHIR_DATA_OPERATION_UPDATE_CODE.equals(activity.getCode())) drugOrder.setAction(Order.Action.REVISE);
        if (FHIR_DATA_OPERATION_ABORT_CODE.equals(activity.getCode())) drugOrder.setAction(Order.Action.DISCONTINUE);
    }

    private Order.Action getOrderAction(String orderAction) {
        for (Order.Action action : Order.Action.values()) {
            if (action.name().equals(orderAction)) return action;
        }
        return Order.Action.NEW;
    }

    private DrugOrder createOrFetchPreviousOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationRequest, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        if (hasPriorPrescription(medicationRequest)) {
            if (isMedicationRequestInSameEncounter(medicationRequest)) {
                return fetchPreviousOrderFromSameEncounter(encounterComposition, medicationRequest, emrEncounter, systemProperties);
            } else {
                String previousOrderUrl = medicationRequest.getPriorPrescription().getReference();
                String prevOrderEncounterId = new EntityReference().parse(Encounter.class, previousOrderUrl);
                String previousOrderRefId = StringUtils.substringAfterLast(previousOrderUrl, "/");
                String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, prevOrderEncounterId, previousOrderRefId);
                OrderIdMapping previousOrderMapping = (OrderIdMapping) idMappingRepository.findByExternalId(externalId, IdMappingType.MEDICATION_ORDER);
                if (previousOrderMapping == null) {
                    throw new RuntimeException(String.format("The previous order with SHR reference [%s] is not yet synced to SHR", previousOrderUrl));
                }
                return (DrugOrder) orderService.getOrderByUuid(previousOrderMapping.getInternalId());
            }
        }
        return null;
    }

    private DrugOrder fetchPreviousOrderFromSameEncounter(ShrEncounterBundle encounterComposition, MedicationRequest medicationRequest, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder previousDrugOrder;
        OrderIdMapping orderIdMapping = fetchOrderByExternalId(encounterComposition.getShrEncounterId(), getIdPart(medicationRequest.getPriorPrescription().getReference()));
        if (orderIdMapping != null) {
            previousDrugOrder = (DrugOrder) orderService.getOrderByUuid(orderIdMapping.getInternalId());
        } else {
            previousDrugOrder = createPreviousOrder(encounterComposition, medicationRequest, emrEncounter, systemProperties);
        }
        return previousDrugOrder;
    }

    private DrugOrder createPreviousOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationRequest, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder previousDrugOrder;
        previousDrugOrder = createDrugOrder(encounterComposition, (MedicationRequest) FHIRBundleHelper.findResourceByReference(encounterComposition.getBundle(), medicationRequest.getPriorPrescription()), emrEncounter, systemProperties);
        emrEncounter.addOrder(previousDrugOrder);
        return previousDrugOrder;
    }

    private void addDrugOrderToIdMapping(DrugOrder drugOrder, MedicationRequest medicationRequest, ShrEncounterBundle encounterComposition, SystemProperties systemProperties) {
        String shrOrderId = getIdPart(medicationRequest.getId());
        String orderUrl = getOrderUrl(encounterComposition, systemProperties, shrOrderId);
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, encounterComposition.getShrEncounterId(), shrOrderId);
        OrderIdMapping orderIdMapping = new OrderIdMapping(drugOrder.getUuid(), externalId, IdMappingType.MEDICATION_ORDER, orderUrl, new Date());
        idMappingRepository.saveOrUpdateIdMapping(orderIdMapping);
    }

    private String getOrderUrl(ShrEncounterBundle encounterComposition, SystemProperties systemProperties, String shrOrderId) {
        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, encounterComposition.getHealthId());
        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, encounterComposition.getShrEncounterId());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new MedicationRequest().getResourceType().name());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrOrderId);
        return new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
    }

    private boolean isMedicationRequestInSameEncounter(MedicationRequest medicationRequest) {
        Reference priorPrescription = medicationRequest.getPriorPrescription();
        if (priorPrescription != null && !priorPrescription.isEmpty()) {
            if (priorPrescription.getReference().startsWith("urn:uuid")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPriorPrescription(MedicationRequest medicationRequest) {
        return medicationRequest.getPriorPrescription() != null && StringUtils.isNotBlank(medicationRequest.getPriorPrescription().getReference());
    }

    private void addCustomDosageToDosingInstructions(Dosage dosageInstruction, HashMap<String, Object> dosingInstructionsMap) {
        List<Extension> extensions = dosageInstruction.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME));
        if (CollectionUtils.isNotEmpty(extensions)) {
            String value = ((StringType) extensions.get(0).getValue()).getValue();
            if (StringUtils.isNotBlank(value)) {
                try {
                    Map map = objectMapper.readValue(value, Map.class);
                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY)) {
                        Double morningDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY);
                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_MORNING_DOSE_KEY, morningDose);
                    }
                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY)) {
                        Double afternoonDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY);
                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_AFTERNOON_DOSE_KEY, afternoonDose);
                    }
                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY)) {
                        Double eveningDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY);
                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_EVENING_DOSE_KEY, eveningDose);
                    }
                } catch (IOException e) {
                    logger.warn("Unable to map the Dosage Instructions extension value");
                }
            }
        }
    }

    private Double getDoseValue(Map map, String doseKey) {
        Object dose = map.get(doseKey);
        return dose != null ? Double.parseDouble(dose.toString()) : null;
    }

    private void setDoseUnits(DrugOrder drugOrder, Dosage dosageInstruction) {
        SimpleQuantity dose = (SimpleQuantity) dosageInstruction.getDose();
        String dosingUnitsConceptUuid = globalPropertyLookUpService.getGlobalPropertyValue(MRSProperties.GLOBAL_PROPERTY_DOSING_FORMS_CONCEPT_UUID);
        if (StringUtils.isBlank(dosingUnitsConceptUuid)) {
            throw new RuntimeException(String.format("Global property %s is not set", MRSProperties.GLOBAL_PROPERTY_DOSING_FORMS_CONCEPT_UUID));
        }
        Concept dosingUnitsConcept = conceptService.getConceptByUuid(dosingUnitsConceptUuid);
        Concept doseUnitConcept = null;
        if (StringUtils.isNotBlank(dose.getCode())) {
            doseUnitConcept = omrsConceptLookup.findMemberConceptFromValueSetCode(dosingUnitsConcept, dose.getCode());
        }
        if (doseUnitConcept == null) {
            doseUnitConcept = omrsConceptLookup.findMemberFromDisplayName(dosingUnitsConcept, dose.getUnit());
        }
        if (doseUnitConcept == null) {
            throw new RuntimeException(String.format("Unable to find the dose units [%s] under dosing units.", StringUtils.isNotBlank(dose.getCode()) ? dose.getCode() : dose.getUnit()));
        }
        drugOrder.setDoseUnits(doseUnitConcept);
    }

    private void addNotesAndInstructionsToDosingInstructions(MedicationRequest medicationRequest, HashMap<String, Object> map) {
        CodeableConcept additionalInstructions = medicationRequest.getDosageInstructionFirstRep().getAdditionalInstructionFirstRep();
        if (additionalInstructions != null && !additionalInstructions.isEmpty()) {
            Concept additionalInstructionsConcept = omrsConceptLookup.findConceptByCodeOrDisplay(additionalInstructions.getCoding());
            if (additionalInstructionsConcept != null)
                map.put(MRSProperties.BAHMNI_DRUG_ORDER_INSTRCTIONS_KEY, additionalInstructionsConcept.getName().getName());
        }

        if (StringUtils.isNotBlank(medicationRequest.getNoteFirstRep().getText()))
            map.put(MRSProperties.BAHMNI_DRUG_ORDER_ADDITIONAL_INSTRCTIONS_KEY, medicationRequest.getNoteFirstRep().getText());
    }

    private void setQuantity(DrugOrder drugOrder, MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest) {
        SimpleQuantity quantity = dispenseRequest.getQuantity();
        drugOrder.setQuantity(quantity.getValue().doubleValue());
        Concept unitConcept = conceptService.getConceptByName(quantity.getUnit());
        if (unitConcept == null)
            unitConcept = conceptService.getConceptByName(MRSProperties.DRUG_ORDER_QUANTITY_UNITS_CONCEPT_NAME);
        drugOrder.setQuantityUnits(unitConcept);
    }

    private void setScheduledDateAndUrgency(DrugOrder drugOrder, Provenance provenance) {
        Date start = provenance.getPeriod().getStart();
        if (start == null) {
            drugOrder.setUrgency(Order.Urgency.ROUTINE);
        } else {
            drugOrder.setScheduledDate(start);
            drugOrder.setUrgency(Order.Urgency.ON_SCHEDULED_DATE);
        }
    }

    private Provider getOrderer(MedicationRequest medicationRequest) {
        Reference prescriber = medicationRequest.getRequester().getAgent();
        String presciberReferenceUrl = null;
        if (prescriber != null && !prescriber.isEmpty()) {
            presciberReferenceUrl = prescriber.getReference();
        }
        return providerLookupService.getProviderByReferenceUrlOrDefault(presciberReferenceUrl);
    }

    private void mapFrequency(DrugOrder drugOrder, Dosage dosageInstruction) {
        Timing.TimingRepeatComponent repeat = dosageInstruction.getTiming().getRepeat();
        if (repeat.getFrequency() > 0) {
            FrequencyMapperUtil.FrequencyUnit frequencyUnit = frequencyMapperUtil.getFrequencyUnitsFromRepeat(repeat);
            Concept frequencyConcept = conceptService.getConceptByName(frequencyUnit.getConceptName());
            OrderFrequency orderFrequency = orderService.getOrderFrequencyByConcept(frequencyConcept);
            drugOrder.setFrequency(orderFrequency);
        }
    }

    private void setOrderDuration(DrugOrder drugOrder, Dosage dosageInstruction) {
        Duration duration = (Duration) dosageInstruction.getTiming().getRepeat().getBounds();
        drugOrder.setDuration(duration.getValue().intValue());
        Timing.UnitsOfTime unitsOfTime;
        try {
            unitsOfTime = Timing.UnitsOfTime.fromCode(duration.getCode());
        } catch (FHIRException e) {
            String message = String.format("Cannot determine duration unit with code %s", duration.getCode());
            throw new RuntimeException(message, e);
        }
        drugOrder.setDurationUnits(conceptService.getConceptByName(durationMapperUtil.getConceptNameFromUnitOfTime(unitsOfTime)));
    }

    private Concept mapRoute(Dosage dosageInstruction) {
        Concept route = null;
        if (null != dosageInstruction.getRoute() && !dosageInstruction.getRoute().getCoding().isEmpty()) {
            route = omrsConceptLookup.findConceptByCode(dosageInstruction.getRoute().getCoding());
            if (route == null) {
                route = conceptService.getConceptByName(dosageInstruction.getRoute().getCoding().get(0).getDisplay());
            }
        }
        return route;
    }

    private void mapDrug(MedicationRequest medicationRequest, DrugOrder drugOrder) {
        CodeableConcept medication = (CodeableConcept) medicationRequest.getMedication();
        Drug drug = omrsConceptLookup.findDrug(medication.getCoding());
        if (drug != null) {
            drugOrder.setDrug(drug);
        } else {
            drugOrder.setDrugNonCoded(medication.getCodingFirstRep().getDisplay());
            String drugOtherConceptUuid = globalPropertyLookUpService.getGlobalPropertyValue(OpenmrsConstants.GP_DRUG_ORDER_DRUG_OTHER);
            drugOrder.setConcept(conceptService.getConceptByUuid(drugOtherConceptUuid));
        }
    }
}
