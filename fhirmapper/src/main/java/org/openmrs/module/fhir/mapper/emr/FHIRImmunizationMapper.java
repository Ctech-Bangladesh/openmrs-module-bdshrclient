package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.BoundCodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Immunization;
import ca.uhn.fhir.model.dstu2.valueset.ImmunizationReasonCodesEnum;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@Component
public class FHIRImmunizationMapper implements FHIRResourceMapper {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof Immunization;
    }

    @Override
    public void map(Bundle bundle, IResource resource, Patient emrPatient, Encounter newEmrEncounter) {
        Immunization immunization = (Immunization) resource;

        Obs immunizationIncidentObs = new Obs();
        immunizationIncidentObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_IMMUNIZATION_INCIDENT_TEMPLATE));

        immunizationIncidentObs.addGroupMember(getVaccinationDate(immunization));
        immunizationIncidentObs.addGroupMember(getVaccineReported(immunization));
        immunizationIncidentObs.addGroupMember(getVaccineRefused(immunization));
        immunizationIncidentObs.addGroupMember(getDosage(immunization));
        immunizationIncidentObs.addGroupMember(getQuantityUnits(immunization));
        immunizationIncidentObs.addGroupMember(getVaccineType(immunization));
        immunizationIncidentObs.addGroupMember(getRoute(immunization));
        addImmunizationReasons(immunization, immunizationIncidentObs);
        addImmunizationRefusalReasons(immunization, immunizationIncidentObs);

        newEmrEncounter.addObs(immunizationIncidentObs);
    }

    private Obs addImmunizationReasons(Immunization immunization, Obs immunizationIncidentObs) {
        Immunization.Explanation explanation = immunization.getExplanation();
        if (explanation != null && !explanation.isEmpty()) {
            List<BoundCodeableConceptDt<ImmunizationReasonCodesEnum>> reasons = explanation.getReason();
            if (reasons != null && !reasons.isEmpty()) {
                Concept immunizationReasonConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_REASON);
                for (BoundCodeableConceptDt<ImmunizationReasonCodesEnum> reason : reasons) {
                    Obs immunizationReasonObs = new Obs();
                    immunizationReasonObs.setConcept(immunizationReasonConcept);
                    immunizationReasonObs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(reason.getCoding()));
                    immunizationIncidentObs.addGroupMember(immunizationReasonObs);
                }
            }
        }
        return null;
    }

    private Obs addImmunizationRefusalReasons(Immunization immunization, Obs immunizationIncidentObs) {
        Immunization.Explanation explanation = immunization.getExplanation();
        if (explanation != null && !explanation.isEmpty()) {
            List<CodeableConceptDt> reasons = explanation.getReasonNotGiven();
            if (reasons != null && !reasons.isEmpty()) {
                Concept immunizationRefusalReasonConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_REFUSAL_REASON);
                for (CodeableConceptDt reason : reasons) {
                    Obs immunizationRefusalReasonObs = new Obs();
                    immunizationRefusalReasonObs.setConcept(immunizationRefusalReasonConcept);
                    immunizationRefusalReasonObs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(reason.getCoding()));
                    immunizationIncidentObs.addGroupMember(immunizationRefusalReasonObs);
                }
            }
        }
        return null;
    }

    private Obs getRoute(Immunization immunization) {
        if (immunization.getRoute() != null && !immunization.getRoute().isEmpty()) {
            Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
            Obs routeOfObservationObs = new Obs();
            routeOfObservationObs.setConcept(routeOfAdministrationConcept);
            routeOfObservationObs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(immunization.getRoute().getCoding()));
            return routeOfObservationObs;
        }
        return null;
    }

    private Obs getVaccineType(Immunization immunization) {
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINE));
        Drug drug = omrsConceptLookup.findDrug(immunization.getVaccineType().getCoding());
        if (drug != null) {
            obs.setValueCoded(drug.getConcept());
        } else {
            obs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(immunization.getVaccineType().getCoding()));
        }
        return obs;
    }

    private Obs getQuantityUnits(Immunization immunization) {
        QuantityDt doseQuantity = immunization.getDoseQuantity();
        Obs quantityUnitsObs = null;
        if (doseQuantity != null && !doseQuantity.isEmpty()) {
            Concept quantityUnit = omrsConceptLookup.findConceptFromValueSetCode(doseQuantity.getSystem(), doseQuantity.getCode());
            if(quantityUnit != null) {
                quantityUnitsObs = new Obs();
                Concept quantityUnitsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.QUANTITY_UNITS);
                quantityUnitsObs.setConcept(quantityUnitsConcept);
                quantityUnitsObs.setValueCoded(quantityUnit);
            }

        }
        return quantityUnitsObs;

    }

    private Obs getDosage(Immunization immunization) {
        QuantityDt doseQuantity = immunization.getDoseQuantity();
        if (doseQuantity != null && !doseQuantity.isEmpty()) {
            Obs obs = new Obs();
            obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_DOSAGE));
            obs.setValueNumeric(doseQuantity.getValue().doubleValue());
            return obs;
        }
        return null;
    }

    private Obs getVaccineReported(Immunization immunization) {
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_REPORTED));
        obs.setValueBoolean(immunization.getReported());
        return obs;
    }

    private Obs getVaccineRefused(Immunization immunization) {
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_REFUSED));
        obs.setValueBoolean(immunization.getWasNotGiven());
        return obs;
    }

    private Obs getVaccinationDate(Immunization immunization) {
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_DATE));
        obs.setValueDate(immunization.getDate());
        return obs;
    }
}
