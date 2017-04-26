package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.DiagnosisIdMapping;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openmrs.module.fhir.FHIRProperties.RESOURCE_MAPPING_URL_FORMAT;
import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;
import static org.openmrs.module.fhir.utils.FHIREncounterUtil.getSHREncounterUrl;

@Component
public class FHIRDiagnosisConditionMapper implements FHIRResourceMapper {
    private final Map<Condition.ConditionVerificationStatus, String> diaConditionStatus = new HashMap<>();
    private final ConceptService conceptService;
    private final OMRSConceptLookup omrsConceptLookup;
    private final IdMappingRepository idMappingsRepository;

    @Autowired
    public FHIRDiagnosisConditionMapper(ConceptService conceptService, OMRSConceptLookup omrsConceptLookup, IdMappingRepository idMappingsRepository) {
        diaConditionStatus.put(Condition.ConditionVerificationStatus.PROVISIONAL, MRSProperties.MRS_DIAGNOSIS_STATUS_PRESUMED);
        diaConditionStatus.put(Condition.ConditionVerificationStatus.CONFIRMED, MRSProperties.MRS_DIAGNOSIS_STATUS_CONFIRMED);
        this.conceptService = conceptService;
        this.omrsConceptLookup = omrsConceptLookup;
        this.idMappingsRepository = idMappingsRepository;
    }

    @Override
    public boolean canHandle(Resource resource) {
        if (resource instanceof Condition) {
            final List<Coding> resourceCoding = ((Condition) resource).getCategory().get(0).getCoding();
            if (resourceCoding == null || resourceCoding.isEmpty()) {
                return false;
            }
            return resourceCoding.get(0).getCode().equalsIgnoreCase(FHIRProperties.FHIR_CONDITION_CODE_DIAGNOSIS);
        }
        return false;
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Condition condition = (Condition) resource;

        Concept diagnosisOrder = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_DIAGNOSIS_ORDER);
        Concept diagnosisCertainty = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_DIAGNOSIS_CERTAINTY);
        Concept codedDiagnosis = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_CODED_DIAGNOSIS);
        Concept visitDiagnosis = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_VISIT_DIAGNOSES);

        Concept bahmniInitialDiagnosis = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_INITIAL_DIAGNOSIS);
        Concept bahmniDiagnosisStatus = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_DIAGNOSIS_STATUS);
        Concept bahmniDiagnosisRevised = conceptService.getConceptByName(MRSProperties.MRS_CONCEPT_NAME_DIAGNOSIS_REVISED);

        Concept diagnosisConceptAnswer = omrsConceptLookup.findConceptByCode(condition.getCode().getCoding());
        Concept diagnosisSeverityAnswer = identifyDiagnosisSeverity(diagnosisOrder);
        Concept diagnosisCertaintyAnswer = identifyDiagnosisCertainty(condition, diagnosisCertainty);

        if (diagnosisConceptAnswer == null) {
            return;
        }

        Obs visitDiagnosisObs = new Obs();
        visitDiagnosisObs.setConcept(visitDiagnosis);

        if (diagnosisSeverityAnswer != null) {
            Obs orderObs = addToObsGroup(visitDiagnosisObs, diagnosisOrder);
            orderObs.setValueCoded(diagnosisSeverityAnswer);
        }

        if (diagnosisCertaintyAnswer != null) {
            Obs certaintyObs = addToObsGroup(visitDiagnosisObs, diagnosisCertainty);
            certaintyObs.setValueCoded(diagnosisCertaintyAnswer);
        }

        Obs codedObs = addToObsGroup(visitDiagnosisObs, codedDiagnosis);
        codedObs.setValueCoded(diagnosisConceptAnswer);

        Obs bahmniDiagRevisedObs = addToObsGroup(visitDiagnosisObs, bahmniDiagnosisRevised);
        bahmniDiagRevisedObs.setValueBoolean(false);

        Obs bahmniInitDiagObs = addToObsGroup(visitDiagnosisObs, bahmniInitialDiagnosis);
        bahmniInitDiagObs.setValueText(visitDiagnosisObs.getUuid());

        saveIdMappingForDiagnosis(condition, visitDiagnosisObs, shrEncounterBundle, systemProperties);

        List<Annotation> note = condition.getNote();
        if (!note.isEmpty())
            visitDiagnosisObs.setComment(note.get(0).getText());
        emrEncounter.addObs(visitDiagnosisObs);

    }

    public void saveIdMappingForDiagnosis(Condition condition, Obs visitDiagnosisObs, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        String encounterUrl = getSHREncounterUrl(shrEncounterBundle.getShrEncounterId(), shrEncounterBundle.getHealthId(), systemProperties);
        String diagnosisId = condition.getId();
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), diagnosisId);
        String diagnosisUrl = String.format(RESOURCE_MAPPING_URL_FORMAT, encounterUrl,
                new Condition().getResourceType().name(), diagnosisId);
        IdMapping diagnosisIdMapping = new DiagnosisIdMapping(visitDiagnosisObs.getUuid(), externalId, diagnosisUrl);
        idMappingsRepository.saveOrUpdateIdMapping(diagnosisIdMapping);
    }

    private Obs addToObsGroup(Obs visitDiagnosisObs, Concept obsConcept) {
        Obs orderObs = new Obs();
        orderObs.setConcept(obsConcept);
        visitDiagnosisObs.addGroupMember(orderObs);
        return orderObs;
    }

    private Concept identifyDiagnosisCertainty(Condition condition, Concept diagnosisCertainty) {
        Condition.ConditionVerificationStatus conditionStatus = condition.getVerificationStatus();
        String status = diaConditionStatus.get(conditionStatus);

        Concept certaintyAnswerConcept = null;

        Collection<ConceptAnswer> answers = diagnosisCertainty.getAnswers();
        for (ConceptAnswer answer : answers) {
            if (answer.getAnswerConcept().getName().getName().equalsIgnoreCase(status)) {
                certaintyAnswerConcept = answer.getAnswerConcept();
            }
        }
        if (certaintyAnswerConcept == null) {
            for (ConceptAnswer answer : answers) {
                if (answer.getAnswerConcept().getName().getName().equals(MRSProperties.MRS_DIAGNOSIS_STATUS_CONFIRMED)) {
                    certaintyAnswerConcept = answer.getAnswerConcept();
                    break;
                }
            }
        }
        return certaintyAnswerConcept;

    }

    private Concept identifyDiagnosisSeverity(Concept diagnosisOrderConcept) {
        Concept severityAnswerConcept = null;
        Collection<ConceptAnswer> answers = diagnosisOrderConcept.getAnswers();
        for (ConceptAnswer answer : answers) {
            if (answer.getAnswerConcept().getName().getName().equals(MRSProperties.MRS_DIAGNOSIS_SEVERITY_PRIMARY)) {
                severityAnswerConcept = answer.getAnswerConcept();
                break;
            }
        }
        return severityAnswerConcept;
    }
}
