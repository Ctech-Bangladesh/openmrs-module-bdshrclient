package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.AgeDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.FamilyMemberHistory;
import ca.uhn.fhir.model.primitive.DateDt;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@Component
public class FHIRFamilyHistoryMapper implements FHIRResourceMapper {
    @Autowired
    private IdMappingsRepository idMappingsRepository;

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private OMRSConceptLookup conceptLookup;

    @Override
    public boolean canHandle(IResource resource) {
        return (resource instanceof FamilyMemberHistory);
    }

    @Override
    public void map(Bundle bundle, IResource resource, Patient emrPatient, Encounter newEmrEncounter) {
        FamilyMemberHistory familyMemberHistory = (FamilyMemberHistory) resource;
        Obs familyHistoryObs = new Obs();
        familyHistoryObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_FAMILY_HISTORY));
        mapRelationships(familyHistoryObs, familyMemberHistory);
        newEmrEncounter.addObs(familyHistoryObs);
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
        Obs relationship = mapRelationship(getCodeSimple(familyMemberHistory));
        if (null != relationship) {
            personObs.addGroupMember(relationship);
        }
    }

    private String getCodeSimple(FamilyMemberHistory relation) {
        CodeableConceptDt relationship = relation.getRelationship();
        if (relationship == null || relationship.isEmpty()) {
            return null;
        }
        List<CodingDt> coding = relationship.getCoding();
        if (coding == null || coding.isEmpty()) {
            return null;
        }
        return coding.get(0).getCode();
    }

    private Obs mapRelationCondition(FamilyMemberHistory.Condition conditon) {
        Obs result = new Obs();
        result.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_CONDITION));
        mapOnsetDate(result, conditon.getOnset());
        mapNotes(result, conditon);
        mapCondition(conditon, result);
        return result;
    }

    private void mapCondition(FamilyMemberHistory.Condition condition, Obs result) {
        Obs value = new Obs();
        Concept answerConcept = getAnswer(condition);
        value.setConcept(conceptService.getConceptByName(MRS_CONCEPT_NAME_RELATIONSHIP_DIAGNOSIS));
        value.setValueCoded(answerConcept);
        result.addGroupMember(value);
    }

    private Concept getAnswer(FamilyMemberHistory.Condition condition) {
        List<CodingDt> coding = condition.getCode().getCoding();
        for (CodingDt code : coding) {
            IdMapping mapping = idMappingsRepository.findByExternalId(code.getCode());
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

    private Obs mapRelationship(String code) {
        if (StringUtils.isNotBlank(code)) {
            Obs result = new Obs();
            result.setConcept(conceptLookup.findTRConceptOfType(TrValueSetType.RELATIONSHIP_TYPE));
            result.setValueCoded(conceptLookup.findValuesetConceptFromTrValuesetType(TrValueSetType.RELATIONSHIP_TYPE, code));
            return result;
        } else {
            return null;
        }
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
