package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.*;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.OrderIdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.openmrs.Order.Action.*;
import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component
public class DrugOrderMapper implements EmrOrderResourceHandler {
    private final ObjectMapper objectMapper;
    @Autowired
    private IdMappingRepository idMappingsRepository;
    @Autowired
    private FrequencyMapperUtil frequencyMapperUtil;
    @Autowired
    private DurationMapperUtil durationMapperUtil;
    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private OMRSConceptLookup omrsConceptLookup;
    @Autowired
    private ProviderLookupService providerLookupService;

    private static final Logger logger = Logger.getLogger(DrugOrderMapper.class);

    public DrugOrderMapper() {
        objectMapper = new ObjectMapper();
    }

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_DRUG_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        List<FHIRResource> fhirResources = new ArrayList<>();
        DrugOrder drugOrder = (DrugOrder) order;
        MedicationRequest medicationOrder = new MedicationRequest();

        medicationOrder.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        medicationOrder.setContext(new Reference().setReference(fhirEncounter.getId()));
        medicationOrder.setSubject(fhirEncounter.getPatient());
        medicationOrder.setAuthoredOn(drugOrder.getDateActivated());

        medicationOrder.setMedication(getMedication(drugOrder));
        medicationOrder.setRequester(getOrdererReference(drugOrder, fhirEncounter));
        medicationOrder.addDosageInstruction(getDoseInstructions(drugOrder, systemProperties));
        setDispenseRequest(drugOrder, medicationOrder);
        medicationOrder.setNote(asList(new Annotation(new StringType(getNotes(drugOrder)))));

        String id = new EntityReference().build(Order.class, systemProperties, order.getUuid());
        medicationOrder.addIdentifier().setValue(id);
        medicationOrder.setId(id);

        FHIRResource fhirResource = createProvenance(medicationOrder.getAuthoredOn(), medicationOrder.getRequester().getAgent(), medicationOrder.getId());
        setStatusAndPriorPrescriptionAndOrderAction(drugOrder, medicationOrder, (Provenance) fhirResource.getResource(), systemProperties);

        fhirResources.add(new FHIRResource("Medication Order", medicationOrder.getIdentifier(), medicationOrder));
        fhirResources.add(fhirResource);
        return fhirResources;
    }

    private void setOrderAction(DrugOrder drugOrder, Provenance provenance) {
        Coding coding = new Coding();
        coding.setSystem(FHIRProperties.FHIR_DATA_OPERATION_VALUESET_URL);

        if (NEW.equals(drugOrder.getAction())) {
            coding.setCode(FHIR_DATA_OPERATION_CREATE_CODE);
        } else if (REVISE.equals(drugOrder.getAction())) {
            coding.setCode(FHIR_DATA_OPERATION_UPDATE_CODE);
        } else if (DISCONTINUE.equals(drugOrder.getAction())) {
            coding.setCode(FHIR_DATA_OPERATION_ABORT_CODE);
        }
        provenance.setActivity(coding);
    }

    private String getNotes(DrugOrder drugOrder) {
        return (String) readFromDoseInstructions(drugOrder, MRSProperties.BAHMNI_DRUG_ORDER_ADDITIONAL_INSTRCTIONS_KEY);
    }

    private void setDispenseRequest(DrugOrder drugOrder, MedicationRequest medicationOrder) {
        MedicationRequest.MedicationRequestDispenseRequestComponent dispenseRequest = new MedicationRequest.MedicationRequestDispenseRequestComponent();
        SimpleQuantity quantity = new SimpleQuantity();
        quantity.setValue(drugOrder.getQuantity());
        quantity.setUnit(drugOrder.getQuantityUnits().getName().getName());
        dispenseRequest.setQuantity(quantity);
        medicationOrder.setDispenseRequest(dispenseRequest);
    }

    private void setStatusAndPriorPrescriptionAndOrderAction(DrugOrder drugOrder, MedicationRequest medicationOrder, Provenance provenance, SystemProperties systemProperties) {
        if (drugOrder.getDateStopped() != null || drugOrder.getAction().equals(DISCONTINUE)) {
            medicationOrder.setStatus(MedicationRequest.MedicationRequestStatus.STOPPED);
            if (drugOrder.getDateStopped() != null) {
                provenance.getPeriod().setEnd(drugOrder.getDateStopped(), TemporalPrecisionEnum.MILLI);
            } else {
                provenance.getPeriod().setEnd(drugOrder.getAutoExpireDate(), TemporalPrecisionEnum.MILLI);
            }
        } else {
            medicationOrder.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        }
        setOrderAction(drugOrder, provenance);
        if (drugOrder.getPreviousOrder() != null) {
            String priorPresecription = setPriorPrescriptionReference(drugOrder, systemProperties);
            medicationOrder.setPriorPrescription(new Reference(priorPresecription));
        }
    }

    private String setPriorPrescriptionReference(DrugOrder drugOrder, SystemProperties systemProperties) {
        if (isEditedInDifferentEncounter(drugOrder)) {
            OrderIdMapping orderIdMapping = (OrderIdMapping) idMappingsRepository.findByInternalId(drugOrder.getPreviousOrder().getUuid(), IdMappingType.MEDICATION_ORDER);
            if (orderIdMapping == null) {
                throw new RuntimeException("Previous order encounter with id [" + drugOrder.getPreviousOrder().getEncounter().getUuid() + "] is not synced to SHR yet.");
            }
            return orderIdMapping.getUri();
        } else {
            return new EntityReference().build(Order.class, systemProperties, drugOrder.getPreviousOrder().getUuid());
        }
    }

    private boolean isEditedInDifferentEncounter(DrugOrder drugOrder) {
        return !drugOrder.getEncounter().equals(drugOrder.getPreviousOrder().getEncounter());
    }

    private MedicationRequest.MedicationRequestRequesterComponent getOrdererReference(Order order, FHIREncounter fhirEncounter) {
        MedicationRequest.MedicationRequestRequesterComponent medicationRequestRequesterComponent = new MedicationRequest.MedicationRequestRequesterComponent();
        if (order.getOrderer() != null) {
            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
            if (providerUrl != null) {
                medicationRequestRequesterComponent.setAgent(new Reference().setReference(providerUrl));
                return medicationRequestRequesterComponent;
            }
        }
        medicationRequestRequesterComponent.setAgent(fhirEncounter.getFirstParticipantReference());
        return medicationRequestRequesterComponent;
    }

    private Dosage getDoseInstructions(DrugOrder drugOrder, SystemProperties systemProperties) {
        Dosage dosageInstruction = new Dosage();

        dosageInstruction.setRoute(getRoute(drugOrder, systemProperties));

        dosageInstruction.setAdditionalInstruction(asList(getAdditionalInstructions(drugOrder)));

        dosageInstruction.setAsNeeded(new BooleanType(drugOrder.getAsNeeded()));

        addTiming(drugOrder, dosageInstruction);
        if (null != drugOrder.getDoseUnits()) {
            SimpleQuantity doseQuantity = getDoseQuantityWithUnitsOnly(drugOrder, systemProperties);
            dosageInstruction.setDose(doseQuantity);
            if (drugOrder.getDose() != null) {
                getDosageInstructionsForGenericDose(drugOrder, dosageInstruction);
            } else {
                getDosageInstructionsWithPredifinedFrequency(drugOrder, dosageInstruction);
            }
        }
        dosageInstruction.getTiming().getRepeat().setBounds(getBounds(drugOrder));
//      TODO: should be a part of provenance
//        Extension scheduledDateExtension = getScheduledDateExtension(drugOrder);
//        if (scheduledDateExtension != null)
//            dosageInstruction.getTiming().addUndeclaredExtension(scheduledDateExtension);

        return dosageInstruction;
    }

    private void addTiming(DrugOrder drugOrder, Dosage dosageInstruction) {
        if (drugOrder.getFrequency() != null) {
            Timing timing = getTimingForOrderFrequencyGiven(drugOrder);
            dosageInstruction.setTiming(timing);
        }
    }

    private void getDosageInstructionsWithPredifinedFrequency(DrugOrder drugOrder, Dosage dosageInstruction) {
        int count = 0;
        HashMap<String, Double> map = new HashMap<>();
        Double morningDose = getDoseValue(drugOrder, BAHMNI_DRUG_ORDER_MORNING_DOSE_KEY);
        if (morningDose != null && morningDose > 0) {
            count++;
            map.put(FHIR_DRUG_ORDER_MORNING_DOSE_KEY, morningDose);
        }
        Double afternoonDose = getDoseValue(drugOrder, BAHMNI_DRUG_ORDER_AFTERNOON_DOSE_KEY);
        if (afternoonDose != null && afternoonDose > 0) {
            count++;
            map.put(FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY, afternoonDose);
        }
        Double eveningDose = getDoseValue(drugOrder, BAHMNI_DRUG_ORDER_EVENING_DOSE_KEY);
        if (eveningDose != null && eveningDose > 0) {
            count++;
            map.put(FHIR_DRUG_ORDER_EVENING_DOSE_KEY, eveningDose);
        }
//        todo: Need to verify what is to be done
//        TimingAbbreviationEnum timingAbbreviationEnum = null;
//        if (count == 0) return;
//
//        else if (count == 1) timingAbbreviationEnum = TimingAbbreviationEnum.QD;
//        else if (count == 2) timingAbbreviationEnum = TimingAbbreviationEnum.BID;
//        else if (count == 3) timingAbbreviationEnum = TimingAbbreviationEnum.TID;
//
//        if (timingAbbreviationEnum != null) {
//            TimingDt timing = new TimingDt();
//            timing.setCode(timingAbbreviationEnum);
//            dosageInstruction.setTiming(timing);
//        }

//        try {
//            String json = objectMapper.writeValueAsString(map);
//            String fhirExtensionUrl = FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME);
//            dosageInstruction.addUndeclaredExtension(false, fhirExtensionUrl, new StringDt(json));
//        } catch (IOException e) {
//            logger.warn("Not able to set dose.");
//        }
    }

    private double getDoseValue(DrugOrder drugOrder, String doseKey) {
        Object dose = readFromDoseInstructions(drugOrder, doseKey);
        return dose != null ? Double.parseDouble(dose.toString()) : 0;
    }

    private void getDosageInstructionsForGenericDose(DrugOrder drugOrder, Dosage dosageInstruction) {
        SimpleQuantity doseQuantity = (SimpleQuantity) dosageInstruction.getDose();
        doseQuantity.setValue(getDoseQuantityValue(drugOrder.getDose()).getValue());
        dosageInstruction.setDose(doseQuantity);
    }

//    private Extension getScheduledDateExtension(DrugOrder drugOrder) {
//        if (drugOrder.getScheduledDate() != null) {
//            DateTime dateTime = new DateTime(drugOrder.getScheduledDate(), TemporalPrecisionEnum.MILLI);
//            return new Extension(false, FHIRProperties.getFhirExtensionUrl(FHIRProperties.SCHEDULED_DATE_EXTENSION_NAME), dateTime);
//        }
//        return null;
//    }

    private DecimalType getDoseQuantityValue(Double drugOrderDose) {
        DecimalType dose = new DecimalType();
        dose.setValue(new BigDecimal(drugOrderDose));
        return dose;
    }

    private CodeableConcept getRoute(DrugOrder drugOrder, SystemProperties systemProperties) {
        CodeableConcept route = null;
        if (null != drugOrder.getRoute()) {
            route = codeableConceptService.getTRValueSetCodeableConcept(drugOrder.getRoute(),
                    TrValueSetType.ROUTE_OF_ADMINISTRATION.getTrPropertyValueSetUrl(systemProperties));
        }
        return route;
    }

    private CodeableConcept getAdditionalInstructions(DrugOrder drugOrder) {
        String doseInstructionsConceptName = (String) readFromDoseInstructions(drugOrder, MRSProperties.BAHMNI_DRUG_ORDER_INSTRCTIONS_KEY);
        if (doseInstructionsConceptName != null) {
            Concept additionalInstructionsConcept = conceptService.getConceptByName(doseInstructionsConceptName);
            return codeableConceptService.addTRCodingOrDisplay(additionalInstructionsConcept);
        }
        return null;
    }

    private Object readFromDoseInstructions(DrugOrder drugOrder, String key) {
        if (StringUtils.isBlank(drugOrder.getDosingInstructions())) return null;
        try {
            Map map = objectMapper.readValue(drugOrder.getDosingInstructions(), Map.class);
            return map.get(key);
        } catch (IOException e) {
            logger.warn(String.format("Unable to map the dosing instructions for order [%s].", drugOrder.getUuid()));
        }
        return null;
    }

    private Timing getTimingForOrderFrequencyGiven(DrugOrder drugOrder) {
        Timing timing = new Timing();
        Timing.TimingRepeatComponent repeat = new Timing.TimingRepeatComponent();

        setFrequencyAndPeriod(drugOrder, repeat);
        timing.setRepeat(repeat);
        return timing;
    }

    private void setFrequencyAndPeriod(DrugOrder drugOrder, Timing.TimingRepeatComponent repeat) {
        String frequencyConceptName = drugOrder.getFrequency().getConcept().getName().getName();
        FrequencyMapperUtil.FrequencyUnit frequencyUnit = frequencyMapperUtil.getFrequencyUnits(frequencyConceptName);
        repeat.setFrequency(frequencyUnit.getFrequency());
        repeat.setPeriod(frequencyUnit.getFrequencyPeriod());
        repeat.setPeriodUnit(frequencyUnit.getUnitOfTime());
    }

    private Duration getBounds(DrugOrder drugOrder) {
        Duration duration = new Duration();
        duration.setValue(drugOrder.getDuration());
        String durationUnit = drugOrder.getDurationUnits().getName().getName();
        Timing.UnitsOfTime unitOfTime = durationMapperUtil.getUnitOfTime(durationUnit);
        duration.setCode(unitOfTime.toCode());
        duration.setSystem(unitOfTime.getSystem());
        return duration;
    }

    private SimpleQuantity getDoseQuantityWithUnitsOnly(DrugOrder drugOrder, SystemProperties systemProperties) {
        Concept doseUnits = drugOrder.getDoseUnits();
        SimpleQuantity doseQuantity = new SimpleQuantity();
        TrValueSetType trValueSetType = determineTrValueSet(doseUnits);
        if (null != trValueSetType && null != idMappingsRepository.findByInternalId(doseUnits.getUuid(), IdMappingType.CONCEPT)) {
            String code = codeableConceptService.getTRValueSetCode(doseUnits);
            doseQuantity.setCode(code);
            doseQuantity.setSystem(trValueSetType.getTrPropertyValueSetUrl(systemProperties));
        }
        doseQuantity.setUnit(doseUnits.getName().getName());
        return doseQuantity;
    }

    private TrValueSetType determineTrValueSet(Concept doseUnits) {
        Concept medicationFormsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.MEDICATION_FORMS);
        Concept medicationPackageFormsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.MEDICATION_PACKAGE_FORMS);
        if (omrsConceptLookup.isAnswerOf(medicationPackageFormsConcept, doseUnits)) {
            return TrValueSetType.MEDICATION_PACKAGE_FORMS;
        } else if (omrsConceptLookup.isAnswerOf(medicationFormsConcept, doseUnits)) {
            return TrValueSetType.MEDICATION_FORMS;
        }
        return null;
    }

    private CodeableConcept getMedication(DrugOrder drugOrder) {
        Coding coding = new Coding();
        if (drugOrder.getDrug() == null) {
            coding.setDisplay(drugOrder.getDrugNonCoded());
        } else {
            String uuid = drugOrder.getDrug().getUuid();
            IdMapping idMapping = idMappingsRepository.findByInternalId(uuid, IdMappingType.MEDICATION);
            String displayName = drugOrder.getDrug().getDisplayName();
            if (null != idMapping) {
                coding.setCode(idMapping.getExternalId())
                        .setSystem(idMapping.getUri())
                        .setDisplay(displayName);
            } else {
                coding.setDisplay(displayName);
            }
        }
        return new CodeableConcept().addCoding(coding);
    }
}
