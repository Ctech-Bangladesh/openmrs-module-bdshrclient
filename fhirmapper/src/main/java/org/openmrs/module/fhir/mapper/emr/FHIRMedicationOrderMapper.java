package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Duration;
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

import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;

@Component
public class FHIRMedicationOrderMapper implements FHIRResourceMapper {
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

    private static final Logger logger = Logger.getLogger(FHIRMedicationOrderMapper.class);

    @Autowired
    public FHIRMedicationOrderMapper(OMRSConceptLookup omrsConceptLookup, ConceptService conceptService,
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
        MedicationRequest medicationOrder = (MedicationRequest) resource;
        if (!shouldSyncMedicationRequest(encounterComposition, medicationOrder)) return;
        DrugOrder drugOrder = createDrugOrder(encounterComposition, medicationOrder, emrEncounter, systemProperties);
        emrEncounter.addOrder(drugOrder);
    }

    private DrugOrder createDrugOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder drugOrder = new DrugOrder();
        DrugOrder previousDrugOrder = createOrFetchPreviousOrder(encounterComposition, medicationOrder, emrEncounter, systemProperties);
        if (previousDrugOrder != null) {
            drugOrder.setPreviousOrder(previousDrugOrder);
        }

        mapDrug(medicationOrder, drugOrder);
        if (medicationOrder.getDosageInstruction().isEmpty()) return null;
        //will work only because any order created through bahmni is activated immediately
        drugOrder.setDateActivated(medicationOrder.getAuthoredOn());
        Dosage dosageInstruction = medicationOrder.getDosageInstructionFirstRep();
        mapFrequency(drugOrder, dosageInstruction);
        HashMap<String, Object> dosingInstructionsMap = new HashMap<>();
        addNotesAndInstructionsToDosingInstructions(medicationOrder, dosingInstructionsMap);
        setOrderDuration(drugOrder, dosageInstruction);
        if (dosageInstruction.getDose() != null) {
            if (((SimpleQuantity) dosageInstruction.getDose()).getValue() != null) {
                drugOrder.setDose(((SimpleQuantity) dosageInstruction.getDose()).getValue().doubleValue());
            } else {
                addCustomDosageToDosingInstructions(dosageInstruction, dosingInstructionsMap);
            }
            setDoseUnits(drugOrder, dosageInstruction);
        }
        setQuantity(drugOrder, medicationOrder.getDispenseRequest());
        setScheduledDateAndUrgency(drugOrder, dosageInstruction);
        setOrderAction(drugOrder, medicationOrder);

        drugOrder.setRoute(mapRoute(dosageInstruction));
        drugOrder.setAsNeeded(((BooleanType) dosageInstruction.getAsNeeded()).getValue());
        drugOrder.setOrderer(getOrderer(medicationOrder));
        drugOrder.setNumRefills(DEFAULT_NUM_REFILLS);
        drugOrder.setCareSetting(orderCareSettingLookupService.getCareSetting());
        try {
            drugOrder.setDosingInstructions(objectMapper.writeValueAsString(dosingInstructionsMap));
        } catch (IOException e) {
            logger.warn("Unable to set dosageInstruction");
        }
        drugOrder.setDosingType(FlexibleDosingInstructions.class);

        addDrugOrderToIdMapping(drugOrder, medicationOrder, encounterComposition, systemProperties);
        return drugOrder;
    }

    private boolean shouldSyncMedicationRequest(ShrEncounterBundle encounterComposition, MedicationRequest medicationOrder) {
        return fetchOrderByExternalId(encounterComposition.getShrEncounterId(), medicationOrder.getId()) == null;
    }

    private OrderIdMapping fetchOrderByExternalId(String shrEncounterId, String medicationOrderId) {
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, medicationOrderId);
        return (OrderIdMapping) idMappingRepository.findByExternalId(externalId, IdMappingType.MEDICATION_ORDER);
    }

    private void setOrderAction(DrugOrder drugOrder, MedicationRequest medicationOrder) {
        List<Extension> extensions = medicationOrder.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.MEDICATIONORDER_ACTION_EXTENSION_NAME));
        if (extensions == null || extensions.isEmpty()) {
            drugOrder.setAction(Order.Action.NEW);
            return;
        }
        Extension orderActionExtension = extensions.get(0);
        StringType orderAction = (StringType) orderActionExtension.getValue();
        drugOrder.setAction(getOrderAction(orderAction.getValue()));
    }

    private Order.Action getOrderAction(String orderAction) {
        for (Order.Action action : Order.Action.values()) {
            if (action.name().equals(orderAction)) return action;
        }
        return Order.Action.NEW;
    }

    private DrugOrder createOrFetchPreviousOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        if (hasPriorPrescription(medicationOrder)) {
            if (isMedicationRequestInSameEncounter(medicationOrder)) {
                return fetchPreviousOrderFromSameEncounter(encounterComposition, medicationOrder, emrEncounter, systemProperties);
            } else {
                String previousOrderUrl = medicationOrder.getPriorPrescription().getReference();
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

    private DrugOrder fetchPreviousOrderFromSameEncounter(ShrEncounterBundle encounterComposition, MedicationRequest medicationOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder previousDrugOrder;
        OrderIdMapping orderIdMapping = fetchOrderByExternalId(encounterComposition.getShrEncounterId(), medicationOrder.getPriorPrescription().getReference());
        if (orderIdMapping != null) {
            previousDrugOrder = (DrugOrder) orderService.getOrderByUuid(orderIdMapping.getInternalId());
        } else {
            previousDrugOrder = createPreviousOrder(encounterComposition, medicationOrder, emrEncounter, systemProperties);
        }
        return previousDrugOrder;
    }

    private DrugOrder createPreviousOrder(ShrEncounterBundle encounterComposition, MedicationRequest medicationOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        DrugOrder previousDrugOrder;
        previousDrugOrder = createDrugOrder(encounterComposition, (MedicationRequest) FHIRBundleHelper.findResourceByReference(encounterComposition.getBundle(), medicationOrder.getPriorPrescription()), emrEncounter, systemProperties);
        emrEncounter.addOrder(previousDrugOrder);
        return previousDrugOrder;
    }

    private void addDrugOrderToIdMapping(DrugOrder drugOrder, MedicationRequest medicationOrder, ShrEncounterBundle encounterComposition, SystemProperties systemProperties) {
        String shrOrderId = medicationOrder.getId();
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

    private boolean isMedicationRequestInSameEncounter(MedicationRequest medicationOrder) {
        Reference priorPrescription = medicationOrder.getPriorPrescription();
        if (priorPrescription != null && !priorPrescription.isEmpty()) {
            if (priorPrescription.getReference().startsWith("urn:uuid")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPriorPrescription(MedicationRequest medicationOrder) {
        return medicationOrder.getPriorPrescription() != null && !medicationOrder.getPriorPrescription().getReference().isEmpty();
    }

    private void addCustomDosageToDosingInstructions(Dosage dosageInstruction, HashMap<String, Object> dosingInstructionsMap) {
//        List<Extension> extensions = dosageInstruction.getUndeclaredExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME));
//        if (CollectionUtils.isNotEmpty(extensions)) {
//            String value = ((StringType) extensions.get(0).getValue()).getValue();
//            if (StringUtils.isNotBlank(value)) {
//                try {
//                    Map map = objectMapper.readValue(value, Map.class);
//                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY)) {
//                        Double morningDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY);
//                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_MORNING_DOSE_KEY, morningDose);
//                    }
//                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY)) {
//                        Double afternoonDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY);
//                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_AFTERNOON_DOSE_KEY, afternoonDose);
//                    }
//                    if (map.containsKey(FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY)) {
//                        Double eveningDose = getDoseValue(map, FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY);
//                        dosingInstructionsMap.put(MRSProperties.BAHMNI_DRUG_ORDER_EVENING_DOSE_KEY, eveningDose);
//                    }
//                } catch (IOException e) {
//                    logger.warn("Unable to map the Dosage Instructions extension value");
//                }
//            }
//        }
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

    private void addNotesAndInstructionsToDosingInstructions(MedicationRequest medicationOrder, HashMap<String, Object> map) {
//        CodeableConcept additionalInstructions = medicationOrder.getDosageInstructionFirstRep().getAdditionalInstructions();
//        if (additionalInstructions != null && !additionalInstructions.isEmpty()) {
//            Concept additionalInstructionsConcept = omrsConceptLookup.findConceptByCodeOrDisplay(additionalInstructions.getCoding());
//            if (additionalInstructionsConcept != null)
//                map.put(MRSProperties.BAHMNI_DRUG_ORDER_INSTRCTIONS_KEY, additionalInstructionsConcept.getName().getName());
//        }
//
//        if (StringUtils.isNotBlank(medicationOrder.getNote()))
//            map.put(MRSProperties.BAHMNI_DRUG_ORDER_ADDITIONAL_INSTRCTIONS_KEY, medicationOrder.getNote());
    }

    private void setQuantity(DrugOrder drugOrder, MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest) {
        SimpleQuantity quantity = dispenseRequest.getQuantity();
        drugOrder.setQuantity(quantity.getValue().doubleValue());
        Concept unitConcept = conceptService.getConceptByName(quantity.getUnit());
        if (unitConcept == null)
            unitConcept = conceptService.getConceptByName(MRSProperties.DRUG_ORDER_QUANTITY_UNITS_CONCEPT_NAME);
        drugOrder.setQuantityUnits(unitConcept);
    }

    private void setScheduledDateAndUrgency(DrugOrder drugOrder, Dosage dosageInstruction) {
        Timing timing = dosageInstruction.getTiming();
        String fhirScheduledDateExtensionUrl = FHIRProperties.getFhirExtensionUrl(FHIRProperties.SCHEDULED_DATE_EXTENSION_NAME);
//        if (timing.getUndeclaredExtensions().size() > 0
//                && timing.getUndeclaredExtensionsByUrl(fhirScheduledDateExtensionUrl).size() > 0) {
//            ExtensionDt scheduledDateExtension = timing.getUndeclaredExtensionsByUrl(fhirScheduledDateExtensionUrl).get(0);
//            drugOrder.setScheduledDate(((DateTimeDt) scheduledDateExtension.getValue()).getValue());
//            drugOrder.setUrgency(Order.Urgency.ON_SCHEDULED_DATE);
//        } else {
//            drugOrder.setUrgency(Order.Urgency.ROUTINE);
//        }
    }

    private Provider getOrderer(MedicationRequest medicationOrder) {
        Reference prescriber = medicationOrder.getRequester().getAgent();
        String presciberReferenceUrl = null;
        if (prescriber != null && !prescriber.isEmpty()) {
            presciberReferenceUrl = prescriber.getReference();
        }
        return providerLookupService.getProviderByReferenceUrlOrDefault(presciberReferenceUrl);
    }

    private void mapFrequency(DrugOrder drugOrder, Dosage dosageInstruction) {
//        Timing.Repeat repeat = dosageInstruction.getTiming().getRepeat();
//        if (repeat.getFrequency() != null) {
//            FrequencyMapperUtil.FrequencyUnit frequencyUnit = frequencyMapperUtil.getFrequencyUnitsFromRepeat(repeat);
//            Concept frequencyConcept = conceptService.getConceptByName(frequencyUnit.getConceptName());
//            OrderFrequency orderFrequency = orderService.getOrderFrequencyByConcept(frequencyConcept);
//            drugOrder.setFrequency(orderFrequency);
//        }
    }

    private void setOrderDuration(DrugOrder drugOrder, Dosage dosageInstruction) {
        Duration duration = (Duration) dosageInstruction.getTiming().getRepeat().getBounds();
        drugOrder.setDuration(duration.getValue().intValue());
//        drugOrder.setDurationUnits(conceptService.getConceptByName(durationMapperUtil.getConceptNameFromUnitOfTime(UnitsOfTimeEnum.VALUESET_BINDER.fromCodeString(duration.getCode()))));
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

    private void mapDrug(MedicationRequest medicationOrder, DrugOrder drugOrder) {
        CodeableConcept medication = (CodeableConcept) medicationOrder.getMedication();
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
