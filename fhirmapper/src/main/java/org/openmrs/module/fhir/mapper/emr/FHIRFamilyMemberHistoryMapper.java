package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class FHIRFamilyMemberHistoryMapper implements FHIRResourceMapper {
    private final IdMappingRepository idMappingsRepository;
    private final ConceptService conceptService;
    private final OMRSConceptLookup conceptLookup;

    @Autowired
    public FHIRFamilyMemberHistoryMapper(IdMappingRepository idMappingsRepository, ConceptService conceptService, OMRSConceptLookup conceptLookup) {
        this.idMappingsRepository = idMappingsRepository;
        this.conceptService = conceptService;
        this.conceptLookup = conceptLookup;
    }

    @Override
    public boolean canHandle(Resource resource) {
        return (resource instanceof FamilyMemberHistory);
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        FamilyMemberHistory familyMemberHistory = (FamilyMemberHistory) resource;
        Obs familyHistoryObs = new Obs();
        familyHistoryObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_FAMILY_HISTORY));
        mapRelationships(familyHistoryObs, familyMemberHistory);
        emrEncounter.addObs(familyHistoryObs);
    }


    private void mapRelationships(Obs familyHistoryObs, FamilyMemberHistory familyMemberHistory) {
        Obs personObs = new Obs();
        personObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_PERSON));
        mapRelation(personObs, familyMemberHistory);
        familyHistoryObs.addGroupMember(personObs);
    }

    private void mapRelation(Obs personObs, FamilyMemberHistory familyMemberHistory) {
        personObs.addGroupMember(setBornOnObs(familyMemberHistory));
        mapRelationship(personObs, familyMemberHistory);
        for (FamilyMemberHistory.FamilyMemberHistoryConditionComponent condition : familyMemberHistory.getCondition()) {
            personObs.addGroupMember(mapRelationCondition(condition));
        }
    }

    private void mapRelationship(Obs personObs, FamilyMemberHistory familyMemberHistory) {
        Obs relationship = mapRelationship(familyMemberHistory.getRelationship());
        if (null != relationship) {
            personObs.addGroupMember(relationship);
        }
    }

    private Obs mapRelationCondition(FamilyMemberHistory.FamilyMemberHistoryConditionComponent conditon) {
        Concept familyMemberConditionAnswerConcept = getAnswer(conditon);
        if (familyMemberConditionAnswerConcept == null) return null;
        Obs result = new Obs();
        result.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_CONDITION));
        mapOnsetDate(result, conditon.getOnset());
        mapNotes(result, conditon);
        mapCondition(familyMemberConditionAnswerConcept, result);
        return result;
    }

    private void mapCondition(Concept answerConcept, Obs result) {
        Obs value = new Obs();
        value.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_DIAGNOSIS));
        value.setValueCoded(answerConcept);
        result.addGroupMember(value);
    }

    private Concept getAnswer(FamilyMemberHistory.FamilyMemberHistoryConditionComponent condition) {
        List<Coding> coding = condition.getCode().getCoding();
        for (Coding code : coding) {
            IdMapping mapping = idMappingsRepository.findByExternalId(code.getCode(), IdMappingType.CONCEPT);
            if (null != mapping) {
                return conceptService.getConceptByUuid(mapping.getInternalId());
            }
        }
        return null;
    }

    private void mapNotes(Obs result, FamilyMemberHistory.FamilyMemberHistoryConditionComponent condition) {
        List<Annotation> note = condition.getNote();
        if (note != null && !note.isEmpty()) {
            Obs notes = new Obs();
            Concept onsetDateConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_NOTES);
            notes.setConcept(onsetDateConcept);

            notes.setValueText(note.get(0).getText());
            result.addGroupMember(notes);
        }
    }

    private void mapOnsetDate(Obs result, Type onset) {
        if (onset != null && !onset.isEmpty() && onset instanceof Age) {
            Obs ageValue = new Obs();
            Concept onsetDateConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_ONSET_AGE);
            ageValue.setConcept(onsetDateConcept);
            ageValue.setValueNumeric(((Age) onset).getValue().doubleValue());
            result.addGroupMember(ageValue);
        }
    }

    private Obs mapRelationship(CodeableConcept relationshipCode) {
        if (relationshipCode != null && !relationshipCode.isEmpty()) {
            Obs result = new Obs();
            result.setConcept(conceptLookup.findTRConceptOfType(TrValueSetType.RELATIONSHIP_TYPE));
            Concept relationshipConcept = conceptLookup.findConceptByCode(relationshipCode.getCoding());
            if (relationshipConcept == null) return null;
            result.setValueCoded(relationshipConcept);
            return result;
        }
        return null;
    }

    private Obs setBornOnObs(FamilyMemberHistory familyMemberHistory) {
        if (familyMemberHistory.getBorn() != null && !familyMemberHistory.getBorn().isEmpty() && familyMemberHistory.getBorn() instanceof DateType) {
            Obs bornOnObs = new Obs();
            Concept bornOnConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_BORN_ON);
            java.util.Date observationValue = ((DateType) familyMemberHistory.getBorn()).getValue();
            bornOnObs.setValueDate(observationValue);
            bornOnObs.setConcept(bornOnConcept);
            return bornOnObs;
        }
        return null;
    }
}
