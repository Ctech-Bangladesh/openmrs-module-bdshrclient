package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.AgeDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.FamilyMemberHistory;
import ca.uhn.fhir.model.primitive.DateDt;
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
    public boolean canHandle(IResource resource) {
        return (resource instanceof FamilyMemberHistory);
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
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
        for (FamilyMemberHistory.Condition condition : familyMemberHistory.getCondition()) {
            personObs.addGroupMember(mapRelationCondition(condition));
        }
    }

    private void mapRelationship(Obs personObs, FamilyMemberHistory familyMemberHistory) {
        Obs relationship = mapRelationship(familyMemberHistory.getRelationship());
        if (null != relationship) {
            personObs.addGroupMember(relationship);
        }
    }

    private Obs mapRelationCondition(FamilyMemberHistory.Condition conditon) {
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

    private Concept getAnswer(FamilyMemberHistory.Condition condition) {
        List<CodingDt> coding = condition.getCode().getCoding();
        for (CodingDt code : coding) {
            IdMapping mapping = idMappingsRepository.findByExternalId(code.getCode(), IdMappingType.CONCEPT);
            if (null != mapping) {
                return conceptService.getConceptByUuid(mapping.getInternalId());
            }
        }
        return null;
    }

    private void mapNotes(Obs result, FamilyMemberHistory.Condition condition) {
        if (condition.getNote() != null && !condition.getNote().isEmpty()) {
            Obs notes = new Obs();
            Concept onsetDateConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_NOTES);
            notes.setConcept(onsetDateConcept);
            notes.setValueText(condition.getNote().getText());
            result.addGroupMember(notes);
        }
    }

    private void mapOnsetDate(Obs result, IDatatype onset) {
        if (onset != null && !onset.isEmpty() && onset instanceof AgeDt) {
            Obs ageValue = new Obs();
            Concept onsetDateConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_ONSET_AGE);
            ageValue.setConcept(onsetDateConcept);
            ageValue.setValueNumeric(((AgeDt) onset).getValue().doubleValue());
            result.addGroupMember(ageValue);
        }
    }

    private Obs mapRelationship(CodeableConceptDt relationshipCode) {
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
        if (familyMemberHistory.getBorn() != null && !familyMemberHistory.getBorn().isEmpty() && familyMemberHistory.getBorn() instanceof DateDt) {
            Obs bornOnObs = new Obs();
            Concept bornOnConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_BORN_ON);
            java.util.Date observationValue = ((DateDt) familyMemberHistory.getBorn()).getValue();
            bornOnObs.setValueDate(observationValue);
            bornOnObs.setConcept(bornOnConcept);
            return bornOnObs;
        }
        return null;
    }
}
