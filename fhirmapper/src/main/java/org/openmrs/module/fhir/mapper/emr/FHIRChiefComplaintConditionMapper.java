package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.FHIRProperties;
import org.openmrs.module.fhir.mapper.MRSProperties;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class FHIRChiefComplaintConditionMapper implements FHIRResourceMapper {

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private ConceptService conceptService;

    private static final int CONVERTION_PARAMETER_FOR_MINUTES = (60 * 1000);

    @Override
    public boolean canHandle(IResource resource) {
        if (resource instanceof Condition) {
            final List<CodingDt> resourceCoding = ((Condition) resource).getCategory().getCoding();
            if (resourceCoding == null || resourceCoding.isEmpty()) {
                return false;
            }
            return resourceCoding.get(0).getCode().equalsIgnoreCase(FHIRProperties.FHIR_CONDITION_CODE_CHIEF_COMPLAINT);
        }
        return false;
    }

    @Override
    public void map(Bundle bundle, IResource resource, Patient emrPatient, Encounter newEmrEncounter, Map<String, List<String>> processedList) {
        Condition condition = (Condition) resource;

        if (isAlreadyProcessed(condition, processedList))
            return;
        Concept historyAndExaminationConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_COMPLAINT_CONDITION_TEMPLATE);
        Concept chiefComplaintDataConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DATA);
        Concept chiefComplaintDurationConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DURATION);

        Obs chiefComplaintObs = new Obs();
        List<CodingDt> conditionCoding = condition.getCode().getCoding();
        Concept conceptAnswer = omrsConceptLookup.findConcept(conditionCoding);
        if (conceptAnswer == null) {
            if (CollectionUtils.isNotEmpty(conditionCoding)) {
                String displayName = conditionCoding.get(0).getDisplay();
                Concept nonCodedChiefComplaintConcept = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_NON_CODED_CHIEF_COMPLAINT);
                chiefComplaintObs.setConcept(nonCodedChiefComplaintConcept);
                chiefComplaintObs.setValueText(displayName);
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

        Obs historyExaminationObs = getHistoryAndExaminationObservation(newEmrEncounter, historyAndExaminationConcept);
        historyExaminationObs.setConcept(historyAndExaminationConcept);
        historyExaminationObs.addGroupMember(chiefComplaintDataObs);
        newEmrEncounter.addObs(historyExaminationObs);

        processedList.put(condition.getIdentifier().get(0).getValue(), Arrays.asList(chiefComplaintDataObs.getUuid()));
    }

    public Obs getHistoryAndExaminationObservation(Encounter newEmrEncounter, Concept historyAndExaminationConcept) {
        Obs historyExaminationObs = findObservationFromEncounter(newEmrEncounter, historyAndExaminationConcept);
        if (historyExaminationObs == null) {
            historyExaminationObs = new Obs();
        }
        return historyExaminationObs;
    }

    public Obs findObservationFromEncounter(Encounter newEmrEncounter, Concept historyAndExaminationConcept) {
        for (Obs obs : newEmrEncounter.getAllObs()) {
            if (obs.getConcept().equals(historyAndExaminationConcept)) {
                return obs;
            }
        }
        return null;
    }

    private boolean hasDuration(Condition condition) {
        return condition.getOnset() != null;
    }

    private boolean isAlreadyProcessed(Condition condition, Map<String, List<String>> processedList) {
        return processedList.containsKey(condition.getIdentifier().get(0).getValue());
    }

    private Double getComplaintDuration(Condition condition) {
        DateTimeDt onsetDateTime = (DateTimeDt) condition.getOnset();
        long differenceInMinutes = (condition.getDateAsserted().getTime() - onsetDateTime.getValue().getTime()) / CONVERTION_PARAMETER_FOR_MINUTES;
        return Double.valueOf(differenceInMinutes);
    }
}
