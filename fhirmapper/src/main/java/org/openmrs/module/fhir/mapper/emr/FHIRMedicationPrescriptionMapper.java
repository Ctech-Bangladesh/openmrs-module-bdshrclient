package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.instance.model.AtomFeed;
import org.hl7.fhir.instance.model.MedicationPrescription;
import org.hl7.fhir.instance.model.Period;
import org.hl7.fhir.instance.model.Quantity;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ResourceType;
import org.hl7.fhir.instance.model.Schedule;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.OrderFrequency;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.UnitsHelpers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.hl7.fhir.instance.model.MedicationPrescription.MedicationPrescriptionDosageInstructionComponent;
import static org.openmrs.module.fhir.utils.DateUtil.parseDate;
import static org.openmrs.module.fhir.utils.UnitsHelpers.UnitToDaysConverter;

@Component
public class FHIRMedicationPrescriptionMapper implements FHIRResource {
    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UnitsHelpers unitsHelpers;

    @Override
    public boolean canHandle(Resource resource) {
        return ResourceType.MedicationPrescription.equals(resource.getResourceType());
    }

    @Override
    public void map(AtomFeed feed, Resource resource, Patient emrPatient, Encounter newEmrEncounter, Map<String, List<String>> processedList) {
        MedicationPrescription prescription = (MedicationPrescription) resource;

        DrugOrder drugOrder = new DrugOrder();
        drugOrder.setPatient(emrPatient);
        Drug drug = mapDrug(prescription);
        if (drug == null) return;
        drugOrder.setDrug(drug);
        MedicationPrescriptionDosageInstructionComponent dosageInstruction = prescription.getDosageInstruction().get(0);
        mapDosageAndRoute(drugOrder, dosageInstruction);
        mapFrequencyAndDurationAndScheduledDate(drugOrder, dosageInstruction);
        newEmrEncounter.addOrder(drugOrder);
    }

    private void mapFrequencyAndDurationAndScheduledDate(DrugOrder drugOrder, MedicationPrescriptionDosageInstructionComponent dosageInstruction) {
        if (dosageInstruction.getTiming() instanceof Schedule) {
            Schedule schedule = (Schedule) dosageInstruction.getTiming();
            UnitToDaysConverter unit = unitsHelpers.getUnitsOfTimeMapper().get(schedule.getRepeat().getUnitsSimple());
            setOrderDuration(drugOrder, schedule, unit);
            setOrderFrequency(drugOrder, schedule, unit);
            setScheduledDate(drugOrder, schedule);
        }
    }

    private void setScheduledDate(DrugOrder drugOrder, Schedule schedule) {
        if (!schedule.getEvent().isEmpty()) {
            Period period = schedule.getEvent().get(0);
            if(period.getStartSimple() != null) {
                drugOrder.setScheduledDate(parseDate(period.getStartSimple().toString()));
            }
        }
    }

    private void setOrderDuration(DrugOrder drugOrder, Schedule schedule, UnitToDaysConverter unit) {
        drugOrder.setDuration(schedule.getRepeat().getCountSimple());
        String units = unit.getUnits();
        Concept durationUnits = conceptService.getConceptByName(units);
        drugOrder.setDurationUnits(durationUnits);
    }

    private void setOrderFrequency(DrugOrder drugOrder, Schedule schedule, UnitToDaysConverter unit) {
        double frequencyPerDay = schedule.getRepeat().getFrequencySimple() / unit.getInDays();
        List<OrderFrequency> orderFrequencies = orderService.getOrderFrequencies(false);
        if (!orderFrequencies.isEmpty()) {
            OrderFrequency closestOrderFrequency = getClosestOrderFrequency(frequencyPerDay, orderFrequencies);
            drugOrder.setFrequency(closestOrderFrequency);
        }
    }

    private OrderFrequency getClosestOrderFrequency(double frequencyPerDay, List<OrderFrequency> orderFrequencies) {
        double minDifference = orderFrequencies.get(0).getFrequencyPerDay();
        OrderFrequency closestOrderFrequency = null;
        for (OrderFrequency orderFrequency : orderFrequencies) {
            double difference = orderFrequency.getFrequencyPerDay() - frequencyPerDay;
            if (abs(difference) <= abs(minDifference)) {
                closestOrderFrequency = orderFrequency;
                minDifference = difference;
            }
        }
        return closestOrderFrequency;
    }

    private void mapDosageAndRoute(DrugOrder drugOrder, MedicationPrescriptionDosageInstructionComponent dosageInstruction) {
        Quantity doseQuantity = dosageInstruction.getDoseQuantity();
        drugOrder.setDose(doseQuantity.getValueSimple().doubleValue());
        drugOrder.setDoseUnits(omrsConceptLookup.findConceptFromValueSetCode(doseQuantity.getSystemSimple(), doseQuantity.getCodeSimple()));
        drugOrder.setRoute(mapRoute(drugOrder, dosageInstruction));
    }

    private Concept mapRoute(DrugOrder drugOrder, MedicationPrescriptionDosageInstructionComponent dosageInstruction) {
        Concept route = null;
        if (!dosageInstruction.getRoute().getCoding().isEmpty()) {
            route = omrsConceptLookup.findConcept(dosageInstruction.getRoute().getCoding());
            if (route == null) {
                route = conceptService.getConceptByName(dosageInstruction.getRoute().getCoding().get(0).getDisplaySimple());
            }
        }
        return route;
    }

    private Drug mapDrug(MedicationPrescription prescription) {
        String drugExternalId = substringAfterLast(prescription.getMedication().getReferenceSimple(), "/");
        Drug drug = omrsConceptLookup.findDrugOrder(drugExternalId);
        if(drug == null) {
            drug = conceptService.getDrugByNameOrId(prescription.getMedication().getDisplaySimple());
        }
        return drug;
    }
}
