package org.openmrs.module.fhir.utils;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.openmrs.*;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class CodeableConceptService {

    @Autowired
    private IdMappingRepository idMappingsRepository;

    public CodeableConcept getFHIRCodeableConcept(String code, String system, String display) {
        CodeableConcept codeableConcept = new CodeableConcept();
        addFHIRCoding(codeableConcept, code, system, display);
        return codeableConcept;
    }

    public void addFHIRCoding(CodeableConcept codeableConcept, String code, String system, String display) {
        Coding coding = codeableConcept.addCoding();
        coding.setCode(code);
        coding.setSystem(system);
        coding.setDisplay(display);
    }

    public CodeableConcept addTRCodingOrDisplay(Concept concept) {
        CodeableConcept codeableConceptDt = addTRCoding(concept);
        if (CollectionUtils.isEmpty(codeableConceptDt.getCoding())) {
            Coding coding = codeableConceptDt.addCoding();
            coding.setDisplay(concept.getName().getName());
        }
        return codeableConceptDt;
    }

    public CodeableConcept addTRCodingOrDisplay(Drug drug) {
        CodeableConcept codeableConceptDt = addTRCoding(drug);
        if (CollectionUtils.isEmpty(codeableConceptDt.getCoding())) {
            Coding coding = codeableConceptDt.addCoding();
            coding.setDisplay(drug.getName());
        }
        return codeableConceptDt;
    }

    public CodeableConcept addTRCoding(Concept concept) {
        CodeableConcept codeableConcept = new CodeableConcept();
        Collection<ConceptMap> conceptMappings = concept.getConceptMappings();
        for (ConceptMap mapping : conceptMappings) {
            if (mapping.getConceptMapType().getUuid().equals(ConceptMapType.SAME_AS_MAP_TYPE_UUID)) {
                addTRCodingsForReferenceTerms(concept, idMappingsRepository, codeableConcept, mapping);
            }
        }
        addTRCodingForConcept(concept, idMappingsRepository, codeableConcept);
        return codeableConcept;
    }

    public CodeableConcept addTRCoding(Drug drug) {
        CodeableConcept codeableConcept = new CodeableConcept();
        IdMapping idMapping = idMappingsRepository.findByInternalId(drug.getUuid(), IdMappingType.MEDICATION);
        if (idMapping != null) {
            addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), drug.getName());
        }
        return codeableConcept;
    }

    private void addTRCodingForConcept(Concept concept, IdMappingRepository idMappingsRepository, CodeableConcept codeableConcept) {
        IdMapping idMapping = idMappingsRepository.findByInternalId(concept.getUuid(), IdMappingType.CONCEPT);
        if (idMapping != null) {
            addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), concept.getName().getName());
        }
    }

    private void addTRCodingsForReferenceTerms(Concept concept, IdMappingRepository idMappingsRepository, CodeableConcept codeableConcept, ConceptMap mapping) {
        ConceptReferenceTerm conceptReferenceTerm = mapping.getConceptReferenceTerm();
        IdMapping idMapping = idMappingsRepository.findByInternalId(conceptReferenceTerm.getUuid(), IdMappingType.CONCEPT_REFERENCE_TERM);
        if (idMapping != null) {
            addFHIRCoding(codeableConcept, conceptReferenceTerm.getCode(), idMapping.getUri(), concept.getName().getName());
        }
    }

    public String getTRValueSetCode(Concept concept) {
        for (ConceptMap mapping : concept.getConceptMappings()) {
            if (mapping.getConceptMapType().getUuid().equals(ConceptMapType.SAME_AS_MAP_TYPE_UUID)) {
                return mapping.getConceptReferenceTerm().getCode();
            }
        }
        for (ConceptName conceptName : concept.getShortNames()) {
            return conceptName.getName();
        }
        return concept.getName().getName();
    }

    public CodeableConcept getTRValueSetCodeableConcept(Concept concept, String valueSetURL) {
        return getTRValueSetCodeableConcept(concept, valueSetURL, new CodeableConcept());
    }

    public CodeableConcept getTRValueSetCodeableConcept(Concept concept, String valueSetURL, CodeableConcept codeableConcept) {
        Coding coding = codeableConcept.addCoding();
        if (null != idMappingsRepository.findByInternalId(concept.getUuid(), IdMappingType.CONCEPT)) {
            coding.setCode(getTRValueSetCode(concept));
            coding.setSystem(valueSetURL);
        }
        coding.setDisplay(concept.getName().getName());
        return codeableConcept;
    }
}
