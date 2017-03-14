package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.AnnotationDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.resource.Immunization;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class FHIRImmunizationMapper implements FHIRResourceMapper {

    private final ConceptService conceptService;
    private final OMRSConceptLookup omrsConceptLookup;

    @Autowired
    public FHIRImmunizationMapper(ConceptService conceptService, OMRSConceptLookup omrsConceptLookup) {
        this.conceptService = conceptService;
        this.omrsConceptLookup = omrsConceptLookup;
    }

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof Immunization;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Immunization immunization = (Immunization) resource;

        Obs immunizationIncidentTmpl = new Obs();
        immunizationIncidentTmpl.setConcept(conceptService.getConceptByName(MRS_CONCEPT_IMMUNIZATION_INCIDENT_TEMPLATE));

        Obs immunizationIncidentGroup = new Obs();
        immunizationIncidentGroup.setConcept(conceptService.getConceptByName(MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP));

        Obs obsForVaccinceCode = createObsForVaccinceCode(immunization);
        if (obsForVaccinceCode.getValueCoded() == null) {
            return;
        }
        immunizationIncidentGroup.addGroupMember(obsForVaccinceCode);
        immunizationIncidentGroup.addGroupMember(getDosage(immunization));
        immunizationIncidentGroup.addGroupMember(getQuantityUnits(immunization));
        immunizationIncidentGroup.addGroupMember(getRoute(immunization));
        immunizationIncidentGroup.addGroupMember(getVaccinationDate(immunization));
        immunizationIncidentGroup.addGroupMember(getVaccineReported(immunization));
        immunizationIncidentGroup.addGroupMember(getVaccineRefused(immunization));
        immunizationIncidentGroup.addGroupMember(getImmunizationStatus(immunization));
        addImmunizationReasons(immunization, immunizationIncidentGroup);
        addImmunizationRefusalReasons(immunization, immunizationIncidentGroup);
        addImmunizationNotes(immunization, immunizationIncidentGroup);

        immunizationIncidentTmpl.addGroupMember(immunizationIncidentGroup);
        emrEncounter.addObs(immunizationIncidentTmpl);
    }

    private void addImmunizationNotes(Immunization immunization, Obs immunizationIncidentGroup) {
        for (AnnotationDt annotationDt : immunization.getNote()) {
            if (StringUtils.isNotBlank(annotationDt.getText())) {
                Obs notesObs = new Obs();
                notesObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_IMMUNIZATION_NOTE));
                notesObs.setValueText(annotationDt.getText());
                immunizationIncidentGroup.addGroupMember(notesObs);
            }
        }
    }

    private Obs getImmunizationStatus(Immunization immunization) {
        Concept statusConcept = omrsConceptLookup.findValuesetConceptFromTrValuesetType(TrValueSetType.IMMUNIZATION_STATUS, immunization.getStatus());
        if (statusConcept == null) return null;
        Obs statusObs = new Obs();
        statusObs.setConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_STATUS));
        statusObs.setValueCoded(statusConcept);
        return statusObs;
    }

    private Obs addImmunizationReasons(Immunization immunization, Obs immunizationIncident) {
        Immunization.Explanation explanation = immunization.getExplanation();
        if (explanation != null && !explanation.isEmpty()) {
            List<CodeableConceptDt> reasons = explanation.getReason();
            if (reasons != null && !reasons.isEmpty()) {
                Concept immunizationReasonConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_REASON);
                for (CodeableConceptDt reason : reasons) {
                    Concept valueCoded = omrsConceptLookup.findConceptByCodeOrDisplay(reason.getCoding());
                    if (valueCoded == null) continue;
                    Obs immunizationReasonObs = new Obs();
                    immunizationReasonObs.setConcept(immunizationReasonConcept);
                    immunizationReasonObs.setValueCoded(valueCoded);
                    immunizationIncident.addGroupMember(immunizationReasonObs);
                }
            }
        }
        return null;
    }

    private Obs addImmunizationRefusalReasons(Immunization immunization, Obs immunizationIncident) {
        Immunization.Explanation explanation = immunization.getExplanation();
        if (explanation != null && !explanation.isEmpty()) {
            List<CodeableConceptDt> reasons = explanation.getReasonNotGiven();
            if (reasons != null && !reasons.isEmpty()) {
                Concept immunizationRefusalReasonConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_REFUSAL_REASON);
                for (CodeableConceptDt reason : reasons) {
                    Concept valueCoded = omrsConceptLookup.findConceptByCodeOrDisplay(reason.getCoding());
                    if (valueCoded == null) continue;
                    Obs immunizationRefusalReasonObs = new Obs();
                    immunizationRefusalReasonObs.setConcept(immunizationRefusalReasonConcept);
                    immunizationRefusalReasonObs.setValueCoded(valueCoded);
                    immunizationIncident.addGroupMember(immunizationRefusalReasonObs);
                }
            }
        }
        return null;
    }

    private Obs getRoute(Immunization immunization) {
        if (immunization.getRoute() != null && !immunization.getRoute().isEmpty()) {
            Concept routeAnswerConcept = omrsConceptLookup.findConceptByCodeOrDisplay(immunization.getRoute().getCoding());
            Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
            if (null == routeAnswerConcept || null == routeOfAdministrationConcept) return null;
            Obs routeOfObservationObs = new Obs();
            routeOfObservationObs.setConcept(routeOfAdministrationConcept);
            routeOfObservationObs.setValueCoded(routeAnswerConcept);
            return routeOfObservationObs;
        }
        return null;
    }

    private Obs createObsForVaccinceCode(Immunization immunization) {
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINE));
        Drug drug = omrsConceptLookup.findDrug(immunization.getVaccineCode().getCoding());
        if (drug != null) {
            obs.setValueCoded(drug.getConcept());
            obs.setValueDrug(drug);
        } else {
            obs.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(immunization.getVaccineCode().getCoding()));
        }
        return obs;
    }

    private Obs getQuantityUnits(Immunization immunization) {
        QuantityDt doseQuantity = immunization.getDoseQuantity();
        Obs quantityUnitsObs = null;
        if (doseQuantity != null && !doseQuantity.isEmpty()) {
            Concept quantityUnit = omrsConceptLookup.findConceptFromValueSetCode(doseQuantity.getSystem(), doseQuantity.getCode());
            if (quantityUnit != null) {
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
        Boolean reported = immunization.getReported();
        if (reported == null) return null;
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_REPORTED));
        obs.setValueBoolean(reported);
        return obs;
    }

    private Obs getVaccineRefused(Immunization immunization) {
        Boolean wasNotGiven = immunization.getWasNotGiven();
        if (wasNotGiven == null) return null;
        Obs obs = new Obs();
        obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_REFUSED));
        obs.setValueBoolean(wasNotGiven);
        return obs;
    }

    private Obs getVaccinationDate(Immunization immunization) {
        if (immunization.getDate() != null) {
            Obs obs = new Obs();
            obs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_VACCINATION_DATE));
            obs.setValueDate(immunization.getDate());
            return obs;
        }
        return null;
    }
}
