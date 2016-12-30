package org.openmrs.module.shrclient.mapper;


import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.Relation;

import java.util.*;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.OpenMRSConstants.*;

public class RelationshipMapper {

    private static String RELATIONSHIP_FTH_TYPE = "FTH";
    private static String RELATIONSHIP_MTH_TYPE = "MTH";
    private static String RELATIONSHIP_SPS_TYPE = "SPS";

    private PersonAttributeMapper personAttributeMapper;
    private PersonService personService;

    private static Map<String, String> attributeRelationshipTypeMapping = new HashMap<String, String>() {{
        put(FATHER_NAME_ATTRIBUTE_TYPE, RELATIONSHIP_FTH_TYPE);
        put(MOTHER_NAME_ATTRIBUTE_TYPE, RELATIONSHIP_MTH_TYPE);
        put(SPOUSE_NAME_ATTRIBUTE_TYPE, RELATIONSHIP_SPS_TYPE);
    }};

    public RelationshipMapper() {
        this.personAttributeMapper = new PersonAttributeMapper();
    }

    public List<Relation> map(Patient openMrsPatient, IdMappingRepository idMappingRepository) {
        List<Relation> relations = new ArrayList<>();

        Set<String> attributeNames = attributeRelationshipTypeMapping.keySet();
        for (String attributeName : attributeNames) {
            PersonAttribute relationshipAttribute = openMrsPatient.getAttribute(attributeName);
            String attributeValue = relationshipAttribute != null ? relationshipAttribute.getValue()
                    : "";
            Relation relation = getRelation(attributeValue, attributeRelationshipTypeMapping.get(attributeName));
            String attributeTypeUuid = getPersonService().getPersonAttributeTypeByName(attributeName).getUuid();
            IdMapping idMapping = idMappingRepository.findByInternalId(getRelationInternalId(openMrsPatient,
                    attributeTypeUuid), IdMappingType.PERSON_RELATION);
            if (idMapping == null) {
                relation.setId(UUID.randomUUID().toString());
                idMapping = new IdMapping(getRelationInternalId(openMrsPatient, attributeTypeUuid),
                        relation.getId(), IdMappingType.PERSON_RELATION, null, new Date());
                idMappingRepository.saveOrUpdateIdMapping(idMapping);
            } else {
                relation.setId(idMapping.getExternalId());
            }
            relations.add(relation);
        }
        return relations;
    }

    public void addRelationAttributes(Relation[] relations, Patient emrPatient, IdMappingRepository idMappingsRepository) {
        // When there are no relations in MCI delete relations from openmrs if present
        if (relations == null || relations.length == 0) {
            for (String relationshipType : attributeRelationshipTypeMapping.keySet()) {
                removeAttribute(emrPatient, relationshipType);
            }
            return;
        }
        // Check if a relationship is not present in MCI delete that relation from openmrs if present
        for (String relationshipType : attributeRelationshipTypeMapping.keySet()) {
            removeAttributeIfRelationNotPresent(relations, emrPatient, relationshipType);
        }
        // Add relations
        List<Relation> relationsToPersistAsAttributes = getRelationsToPersistAsAttributes(relations);
        for (Relation relationToPersistAsAttribute : relationsToPersistAsAttributes) {
            PersonAttribute relationAttribute = getPersonAttribute(relationToPersistAsAttribute, emrPatient, idMappingsRepository);
            if (relationAttribute != null) {
                emrPatient.addAttribute(relationAttribute);
            }
        }
    }

    private String getRelationInternalId(Patient openMrsPatient, String attributeUuid) {
        return String.format("%s:%s", openMrsPatient.getUuid(), attributeUuid);
    }

    private void removeAttributeIfRelationNotPresent(Relation[] relations, Patient emrPatient, String attributeName) {
        Relation relation = getRelationByAttribute(relations, attributeName);
        if (relation == null) {
            removeAttribute(emrPatient, attributeName);
        }
    }

    private void removeAttribute(Patient emrPatient, String attributeName) {
        PersonAttribute attribute = emrPatient.getAttribute(attributeName);
        if (attribute != null) {
            emrPatient.removeAttribute(attribute);
        }
    }

    private Relation getRelationByAttribute(Relation[] relations, String attributeName) {
        for (Relation relation : relations) {
            String relationType = attributeRelationshipTypeMapping.get(attributeName);
            if (relation.getType().equals(relationType)) {
                return relation;
            }
        }
        return null;
    }

    private PersonAttribute getPersonAttribute(Relation relationToPersistAsAttribute, Patient patient, IdMappingRepository idMappingsRepository) {
        String attributeName = getAttributeName(relationToPersistAsAttribute.getType());
        String name = getName(relationToPersistAsAttribute);
        IdMapping idMapping = idMappingsRepository.findByExternalId(relationToPersistAsAttribute.getId(), IdMappingType.PERSON_RELATION);
        PersonAttribute personAttribute = personAttributeMapper.getAttribute(attributeName, name);
        if (idMapping == null) {
            String relationInternalId = getRelationInternalId(patient, personAttribute.getAttributeType().getUuid());
            idMapping = new IdMapping(relationInternalId, relationToPersistAsAttribute.getId(), IdMappingType.PERSON_RELATION, null, new Date());
            idMappingsRepository.saveOrUpdateIdMapping(idMapping);
        }
        return personAttribute;
    }

    private String getName(Relation relation) {
        return StringUtils.trim(String.format("%s %s", StringUtils.trimToEmpty(relation.getGivenName()), StringUtils.trimToEmpty(relation.getSurName())));
    }

    private static List<Relation> getRelationsToPersistAsAttributes(Relation[] relations) {
        List<Relation> relationList = asList(relations);
        List<Relation> relationsToPersistAsAttributes = new ArrayList<>();
        relationsToPersistAsAttributes.addAll(CollectionUtils.select(relationList, new Predicate() {
            @Override
            public boolean evaluate(Object o) {
                Relation relation = (Relation) o;
                return attributeRelationshipTypeMapping.values().contains(relation.getType());
            }
        }));

        return relationsToPersistAsAttributes;

    }

    private String getAttributeName(String relationName) {
        for (String attributeName : attributeRelationshipTypeMapping.keySet()) {
            if (relationName.equals(attributeRelationshipTypeMapping.get(attributeName))) {
                return attributeName;
            }
        }
        return null;
    }

    private Relation getRelation(String relationshipValue, String relationshipType) {
        String givenName = StringUtils.trimToNull(StringUtils.substringBeforeLast(relationshipValue, " "));
        String surName = StringUtils.trimToNull(StringUtils.substringAfterLast(relationshipValue, " "));

        Relation relation = new Relation();
        relation.setType(relationshipType);
        relation.setGivenName(givenName);
        relation.setSurName(surName);
        return relation;
    }

    private PersonService getPersonService() {
        if (personService == null) {
            personService = Context.getPersonService();
        }
        return personService;
    }
}
