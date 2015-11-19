package org.openmrs.module.fhir.utils;

import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.Drug;
import org.openmrs.api.ConceptService;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.apache.commons.collections4.CollectionUtils.exists;
import static org.apache.commons.collections4.CollectionUtils.select;
import static org.openmrs.module.fhir.Constants.*;

@Component
public class OMRSConceptLookup {

    private ConceptService conceptService;
    private IdMappingsRepository idMappingsRepository;
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    public static final String TR_DRUG_REST_URL = "/ws/rest/v1/tr/drugs";
    public static final String WS_REST_V1_TR_CONCEPTS = "/ws/rest/v1/tr/concepts/";

    private Logger logger = Logger.getLogger(OMRSConceptLookup.class);
    private ConceptMapType conceptMapTypeByName;
    private ConceptClass conceptClassByUuid;
    private ConceptDatatype conceptDatatypeByUuid;

    @Autowired
    public OMRSConceptLookup(ConceptService conceptService, IdMappingsRepository repository, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.conceptService = conceptService;
        this.idMappingsRepository = repository;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    public Concept findConceptByCodeOrDisplay(List<CodingDt> codings) {
        Concept conceptByCode = findConceptByCode(codings);
        return conceptByCode != null ? conceptByCode : conceptService.getConceptByName(codings.get(0).getDisplay());
    }

    public Concept findConceptByCode(List<CodingDt> codings) {
        Map<ConceptReferenceTerm, String> referenceTermMap = new HashMap<>();
        Concept identifiedConcept = null;
        for (CodingDt coding : codings) {
            if (isValueSetUrl(coding.getSystem())) {
                identifiedConcept = findConceptFromValueSetCode(coding.getSystem(), coding.getCode());
            } else {
                String uuid = getUuid(coding.getSystem());
                if (StringUtils.isNotBlank(uuid)) {
                    IdMapping idMapping = idMappingsRepository.findByExternalId(uuid);
                    if (idMapping != null) {
                        if (ID_MAPPING_CONCEPT_TYPE.equalsIgnoreCase(idMapping.getType())) {
                            identifiedConcept = conceptService.getConceptByUuid(idMapping.getInternalId());
                        } else if (ID_MAPPING_REFERENCE_TERM_TYPE.equalsIgnoreCase(idMapping.getType())) {
                            referenceTermMap.put(conceptService.getConceptReferenceTermByUuid(idMapping.getInternalId()), coding.getDisplay());
                        }
                    }
                }
            }

            if (identifiedConcept != null) {
                return identifiedConcept;
            }
        }

        return findConceptByReferenceTermMapping(referenceTermMap);
    }

    public Drug findDrug(List<CodingDt> codings) {
        for (CodingDt coding : codings) {
            if (isTRDrugSystem(coding.getSystem())) {
                Drug drug = findDrug(coding.getCode());
                if (drug != null) {
                    return drug;
                } else {
                    throw new RuntimeException(String.format("TR Drug with external id [%s] is not yet synced", coding.getCode()));
                }
            }
        }
        return null;
    }

    public Concept findConceptFromValueSetCode(String system, String code) {
        String valueSet = StringUtils.replace(StringUtils.substringAfterLast(system, "/"), "-", " ");
        Concept valueSetConcept = conceptService.getConceptByName(valueSet);
        Concept answerConcept = findAnswerConceptFromValueSetCode(valueSetConcept, code);
        return answerConcept == null ? conceptService.getConceptByName(code) : answerConcept;
    }

    public boolean referenceTermCodeFound(Concept concept, final String code) {
        return exists(concept.getConceptMappings(), new Predicate<ConceptMap>() {
            @Override
            public boolean evaluate(ConceptMap conceptMap) {
                return conceptMap.getConceptReferenceTerm().getCode().equals(code);
            }
        });
    }

    public boolean shortNameFound(Concept concept, final String code) {
        return exists(concept.getShortNames(), new Predicate<ConceptName>() {
            @Override
            public boolean evaluate(ConceptName conceptName) {
                return conceptName.getName().equals(code);
            }
        });
    }

    public Concept findTRConceptOfType(TrValueSetType type) {
        String globalPropertyValue = globalPropertyLookUpService.getGlobalPropertyValue(type.getGlobalPropertyKey());
        if (globalPropertyValue != null) {
            return conceptService.getConcept(Integer.parseInt(globalPropertyValue));
        } else {
            return conceptService.getConceptByName(type.getDefaultConceptName());
        }
    }

    public Concept findValuesetConceptFromTrValuesetType(TrValueSetType type, String valuesetCode) {
        Concept valuesetConcept = findTRConceptOfType(type);
        Concept answerConcept = findAnswerConceptFromValueSetCode(valuesetConcept, valuesetCode);
        return answerConcept == null ? conceptService.getConceptByName(valuesetCode) : answerConcept;
    }

    public Concept findAnswerConceptFromValueSetCode(Concept codedConcept, String valueSetCode) {
        if (codedConcept != null) {
            for (ConceptAnswer answer : codedConcept.getAnswers(false)) {
                Concept answerConcept = answer.getAnswerConcept();
                if (isConceptForValuesetCode(valueSetCode, answerConcept))
                    return answerConcept;
            }
        }
        return null;
    }

    public Concept findMemberConceptFromValueSetCode(Concept parentConcept, String code) {
        if (parentConcept != null) {
            for (Concept memberConcept : parentConcept.getSetMembers()) {
                if (isConceptForValuesetCode(code, memberConcept))
                    return memberConcept;
            }
        }
        return null;
    }

    public Concept findMemberFromDisplayName(Concept parentConcept, final String name) {
        Collection<Concept> matchedConcepts = select(parentConcept.getSetMembers(), new Predicate<Concept>() {
            @Override
            public boolean evaluate(Concept concept) {
                return exists(concept.getNames(), new Predicate<ConceptName>() {
                    @Override
                    public boolean evaluate(ConceptName conceptName) {
                        return conceptName.getName().equals(name);
                    }
                });
            }
        });
        return matchedConcepts.size() > 0 ? matchedConcepts.iterator().next() : null;
    }

    public boolean isSetMemberOf(Concept parentConcept, final Concept childConcept) {
        return exists(parentConcept.getSetMembers(), new Predicate<Concept>() {
            @Override
            public boolean evaluate(Concept concept) {
                return concept.equals(childConcept);
            }
        });
    }

    public boolean isAnswerOf(Concept parentConcept, final Concept childConcept) {
        return exists(parentConcept.getAnswers(), new Predicate<ConceptAnswer>() {
            @Override
            public boolean evaluate(ConceptAnswer conceptAnswer) {
                return conceptAnswer.getAnswerConcept().equals(childConcept);
            }
        });
    }

    private Drug findDrug(String drugExternalId) {
        IdMapping idMapping = idMappingsRepository.findByExternalId(drugExternalId);
        if (idMapping != null) {
            return conceptService.getDrugByUuid(idMapping.getInternalId());
        }
        return null;
    }

    private boolean isValueSetUrl(String systemSimple) {
        return StringUtils.contains(systemSimple, "tr/vs/");
    }

    private boolean isTRDrugSystem(String systemSimple) {
        return StringUtils.contains(systemSimple, TR_DRUG_REST_URL);
    }

    private boolean isConceptForValuesetCode(String valueSetCode, Concept concept) {
        return referenceTermCodeFound(concept, valueSetCode) || shortNameFound(concept, valueSetCode) || fullNameMatchFound(concept, valueSetCode);
    }

    private boolean fullNameMatchFound(Concept concept, String code) {
        return concept.getName().getName().equals(code);
    }

    private Concept findConceptByReferenceTermMapping(Map<ConceptReferenceTerm, String> referenceTermMapping) {
        if (referenceTermMapping.size() == 0) {
            return null;
        }

        ConceptReferenceTerm refTerm = null;
        String displayString = null;
        for (ConceptReferenceTerm referenceTerm : referenceTermMapping.keySet()) {
            List<Concept> concepts = conceptService.getConceptsByMapping(referenceTerm.getCode(), referenceTerm.getConceptSource().getName());
            for (Concept concept : concepts) {
                String display = referenceTermMapping.get(referenceTerm);
                if (StringUtils.isBlank(display)) continue;
                else {
                    refTerm = referenceTerm;
                    displayString = display;
                    for (ConceptName conceptName : concept.getNames()) {
                        if (conceptName.getName().equalsIgnoreCase(displayString)) {
                            return concept;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String getUuid(String content) {
        return StringUtils.substringAfterLast(content, "/");
    }

    public Concept findConceptByCodings(List<CodingDt> codings, String facilityId) {
        String conceptName = codings.get(0).getDisplay();
        if (hasTRConceptReference(codings)) {
            String message = String.format("Can not create observation, concept %s not yet synced", conceptName);
            logger.error(message);
            throw new RuntimeException(message);
        }
        String fullySpecifiedName = conceptName + UNVERIFIED_BY_TR;
        Concept concept = conceptService.getConceptByName(fullySpecifiedName);
        if (concept != null) return concept;
        else {
            concept = createNewConcept(conceptName, facilityId);
            addReferenceTermMappings(concept, codings);
            return conceptService.saveConcept(concept);
        }
    }

    private void addReferenceTermMappings(Concept concept, List<CodingDt> codings) {
        for (CodingDt coding : codings) {
            if (isValueSetUrl(coding.getSystem())) continue;
            String uuid = getUuid(coding.getSystem());
            if (StringUtils.isNotBlank(uuid)) {
                IdMapping idMapping = idMappingsRepository.findByExternalId(uuid);
                if (idMapping == null) continue;
                if (ID_MAPPING_REFERENCE_TERM_TYPE.equalsIgnoreCase(idMapping.getType())) {
                    ConceptMapType mapTypeMayBe = getMayBeAConceptMapType();
                    ConceptReferenceTerm refTerm = conceptService.getConceptReferenceTermByUuid(idMapping.getInternalId());
                    concept.addConceptMapping(new ConceptMap(refTerm, mapTypeMayBe));
                }
            }
        }
    }

    private ConceptMapType getMayBeAConceptMapType() {
        if (conceptMapTypeByName != null) return conceptMapTypeByName;
        conceptMapTypeByName = conceptService.getConceptMapTypeByName(CONCEPT_MAP_TYPE_MAY_BE_A);
        return conceptMapTypeByName;
    }

    private boolean hasTRConceptReference(List<CodingDt> codings) {
        for (CodingDt coding : codings) {
            if (org.apache.commons.lang3.StringUtils.isNotBlank(coding.getSystem()) && coding.getSystem().contains(WS_REST_V1_TR_CONCEPTS)) {
                return true;
            }
        }
        return false;
    }

    private Concept createNewConcept(String conceptName, String facilityId) {
        Concept concept;
        concept = new Concept();
        concept.setFullySpecifiedName(new ConceptName(conceptName + UNVERIFIED_BY_TR, Locale.ENGLISH));
        concept.setShortName(new ConceptName(conceptName, Locale.ENGLISH));
        concept.setConceptClass(getMiscConceptClass());
        concept.setDatatype(getTextConceptDatatype());
        String version = String.format("%s%s", LOCAL_CONCEPT_VERSION_PREFIX, facilityId);
        concept.setVersion(version);
        return concept;
    }

    private ConceptDatatype getTextConceptDatatype() {
        if (conceptDatatypeByUuid != null) return conceptDatatypeByUuid;
        conceptDatatypeByUuid = conceptService.getConceptDatatypeByUuid(ConceptDatatype.TEXT_UUID);
        return conceptDatatypeByUuid;
    }

    private ConceptClass getMiscConceptClass() {
        if (conceptClassByUuid != null) return conceptClassByUuid;
        conceptClassByUuid = conceptService.getConceptClassByUuid(ConceptClass.MISC_UUID);
        return conceptClassByUuid;
    }
}
