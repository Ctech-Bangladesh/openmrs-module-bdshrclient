package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FHIRChiefComplaintConditionMapper implements FHIRResourceMapper {

    private final OMRSConceptLookup omrsConceptLookup;
    private final ConceptService conceptService;

    private static final int CONVERTION_PARAMETER_FOR_MINUTES = (60 * 1000);

    @Autowired
    public FHIRChiefComplaintConditionMapper(OMRSConceptLookup omrsConceptLookup, ConceptService conceptService) {
        this.omrsConceptLookup = omrsConceptLookup;
        this.conceptService = conceptService;
    }

    @Override
    public boolean canHandle(Resource resource) {
        if (resource instanceof Condition) {
            final List<Coding> resourceCoding = ((Condition) resource).getCategory().get(0).getCoding();
            if (resourceCoding == null || resourceCoding.isEmpty()) {
                return false;
            }
            return resourceCoding.get(0).getCode().equalsIgnoreCase(MRSProperties.TR_CONDITION_CATEGORY_COMPLAINT_CODE);
        }
        return false;
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Condition condition = (Condition) resource;

        Concept historyAndExaminationConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_COMPLAINT_CONDITION_TEMPLATE);
        Concept chiefComplaintDataConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DATA);
        Concept chiefComplaintDurationConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DURATION);

        Obs chiefComplaintObs = new Obs();
        List<Coding> conditionCoding = condition.getCode().getCoding();
        Concept conceptAnswer = omrsConceptLookup.findConceptByCode(conditionCoding);
        if (conceptAnswer == null) {
            if (CollectionUtils.isNotEmpty(conditionCoding)) {
                String displayName = conditionCoding.get(0).getDisplay();
                Concept nonCodedChiefComplaintConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_NON_CODED_CHIEF_COMPLAINT);
                chiefComplaintObs.setConcept(nonCodedChiefComplaintConcept);
                String valueText = StringUtils.isNotBlank(displayName) ? displayName : "";
                chiefComplaintObs.setValueText(valueText);
            } else {
                return;
            }
        } else {
            Concept chiefComplaintConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT);
            chiefComplaintObs.setConcept(chiefComplaintConcept);
            chiefComplaintObs.setValueCoded(conceptAnswer);
        }

        Obs chiefComplaintDataObs = new Obs();
        chiefComplaintDataObs.setConcept(chiefComplaintDataConcept);
        chiefComplaintDataObs.addGroupMember(chiefComplaintObs);

        if (hasDuration(condition)) {
            Obs chiefComplaintDurationObs = new Obs();
            chiefComplaintDurationObs.setConcept(chiefComplaintDurationConcept);
            chiefComplaintDurationObs.setValueNumeric(getComplaintDuration(condition));
            chiefComplaintDataObs.addGroupMember(chiefComplaintDurationObs);
        }

        Obs historyExaminationObs = getHistoryAndExaminationObservation(emrEncounter, historyAndExaminationConcept);
        historyExaminationObs.setConcept(historyAndExaminationConcept);
        historyExaminationObs.addGroupMember(chiefComplaintDataObs);
        emrEncounter.addObs(historyExaminationObs);
    }

    private Obs getHistoryAndExaminationObservation(EmrEncounter newEmrEncounter, Concept historyAndExaminationConcept) {
        Obs historyExaminationObs = findObservationFromEncounter(newEmrEncounter, historyAndExaminationConcept);
        if (historyExaminationObs == null) {
            historyExaminationObs = new Obs();
        }
        return historyExaminationObs;
    }

    private Obs findObservationFromEncounter(EmrEncounter newEmrEncounter, Concept historyAndExaminationConcept) {
        for (Obs obs : newEmrEncounter.getTopLevelObs()) {
            if (obs.getConcept().equals(historyAndExaminationConcept)) {
                return obs;
            }
        }
        return null;
    }

    private boolean hasDuration(Condition condition) {
        return condition.getOnset() != null;
    }

    private Double getComplaintDuration(Condition condition) {
        Period onsetPeriod = (Period) condition.getOnset();
        long differenceInMinutes = (onsetPeriod.getEnd().getTime() - onsetPeriod.getStart().getTime()) / CONVERTION_PARAMETER_FOR_MINUTES;
        return Double.valueOf(differenceInMinutes);
    }
}
