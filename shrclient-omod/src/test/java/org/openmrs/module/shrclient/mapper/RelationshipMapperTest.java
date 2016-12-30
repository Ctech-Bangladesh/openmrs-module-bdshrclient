package org.openmrs.module.shrclient.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.BeanPropertyValueEqualsPredicate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.module.fhir.OpenMRSConstants;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.Relation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.OpenMRSConstants.FATHER_NAME_ATTRIBUTE_TYPE;
import static org.openmrs.module.fhir.OpenMRSConstants.SPOUSE_NAME_ATTRIBUTE_TYPE;

public class RelationshipMapperTest {

    @Mock
    private PersonService personService;
    @Mock
    private IdMappingRepository idMappingsRepository;

    @Before
    public void setup() throws Exception {
        initMocks(this);
        when(personService.getPersonAttributeTypeByName(OpenMRSConstants.FATHER_NAME_ATTRIBUTE_TYPE)).thenReturn(getAttributeType(OpenMRSConstants.FATHER_NAME_ATTRIBUTE_TYPE));
        when(personService.getPersonAttributeTypeByName(OpenMRSConstants.SPOUSE_NAME_ATTRIBUTE_TYPE)).thenReturn(getAttributeType(OpenMRSConstants.SPOUSE_NAME_ATTRIBUTE_TYPE));
        when(personService.getPersonAttributeTypeByName(OpenMRSConstants.MOTHER_NAME_ATTRIBUTE_TYPE)).thenReturn(getAttributeType(OpenMRSConstants.MOTHER_NAME_ATTRIBUTE_TYPE));

        Context context = new Context();
        ServiceContext serviceContext = ServiceContext.getInstance();
        serviceContext.setService(PersonService.class, personService);
        context.setServiceContext(serviceContext);
    }

    @Test
    public void shouldMapGivenNameSurnameAndTypeOfARelation() throws Exception {
        org.openmrs.Patient patientWithFather = new Patient();
        createOpenMrsPersonAttributes(patientWithFather, new HashMap<String, String>() {{
            put(FATHER_NAME_ATTRIBUTE_TYPE, "GivenName SurName");
        }});

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(patientWithFather, idMappingsRepository));
        assertEquals(1, relations.size());
        assertRelation(relations.get(0), "GivenName", "SurName", "FTH");
    }

    @Test
    public void shouldCreateIdMappingIfNotFound() throws Exception {
        org.openmrs.Patient patient = new Patient();
        PersonAttributeType attributeType = getAttributeType(FATHER_NAME_ATTRIBUTE_TYPE);
        patient.addAttribute(new PersonAttribute(attributeType, "GivenName SurName"));

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(patient, idMappingsRepository));
        assertEquals(1, relations.size());
        verify(idMappingsRepository, times(3)).saveOrUpdateIdMapping(any(IdMapping.class));
    }

    @Test
    public void shouldUpdateRelationIfIdMappingFound() throws Exception {
        mockPersonServiceInContext();

        org.openmrs.Patient patient = new Patient();
        PersonAttributeType attributeType = getAttributeType(FATHER_NAME_ATTRIBUTE_TYPE);
        patient.addAttribute(new PersonAttribute(attributeType, "GivenName SurName"));

        String attibuteInternalId = String.format("%s:%s", patient.getUuid(), attributeType.getUuid());
        String externalRelationId = UUID.randomUUID().toString();
        IdMapping relationIdMapping = new IdMapping(attibuteInternalId, externalRelationId, IdMappingType.PERSON_RELATION, null, new Date());

        when(personService.getPersonAttributeTypeByName(FATHER_NAME_ATTRIBUTE_TYPE)).thenReturn(attributeType);
        when(idMappingsRepository.findByInternalId(attibuteInternalId, IdMappingType.PERSON_RELATION)).thenReturn(relationIdMapping);

        List<Relation> relationsWithNames = filterRelationsWithNames(new RelationshipMapper().map(patient, idMappingsRepository));

        assertEquals(1, relationsWithNames.size());
        assertEquals(externalRelationId, relationsWithNames.get(0).getId());
        verify(idMappingsRepository, times(0)).saveOrUpdateIdMapping(relationIdMapping);
    }

    @Test
    public void shouldMapAllRelations() throws Exception {
        org.openmrs.Patient patientWithSpouseAndFather = new Patient();
        createOpenMrsPersonAttributes(patientWithSpouseAndFather, new HashMap<String, String>() {{
            put(FATHER_NAME_ATTRIBUTE_TYPE, "GivenName SurName");
            put(SPOUSE_NAME_ATTRIBUTE_TYPE, "GivenSpouseName SurNameOfSpouse");
        }});

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(patientWithSpouseAndFather, idMappingsRepository));
        assertEquals(2, relations.size());
        assertRelation(relations.get(0), "GivenName", "SurName", "FTH");
        assertRelation(relations.get(1), "GivenSpouseName", "SurNameOfSpouse", "SPS");
    }

    @Test
    public void shouldMapWhenNoRelationsArePresent() throws Exception {
        org.openmrs.Patient orphanPatient = new org.openmrs.Patient();

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(orphanPatient, idMappingsRepository));
        assertEquals(0, relations.size());
    }

    @Test
    public void shouldMapGivenNameWhenThereIsOnlyOneNameProvided() throws Exception {
        org.openmrs.Patient patientWithOneName = new Patient();
        createOpenMrsPersonAttributes(patientWithOneName, new HashMap<String, String>() {{
            put(FATHER_NAME_ATTRIBUTE_TYPE, "OneName");
        }});

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(patientWithOneName, idMappingsRepository));
        assertEquals(1, relations.size());
        assertRelation(relations.get(0), "OneName", null, "FTH");
    }

    @Test
    public void shouldMapSurnameWithLastWordWhenNameHasMultipleWords() throws Exception {
        org.openmrs.Patient patientWithOneName = new Patient();
        createOpenMrsPersonAttributes(patientWithOneName, new HashMap<String, String>() {{
            put(FATHER_NAME_ATTRIBUTE_TYPE, "One Two Three Four         LastName");
        }});

        List<Relation> relations = filterRelationsWithNames(new RelationshipMapper().map(patientWithOneName, idMappingsRepository));
        assertEquals(1, relations.size());
        assertRelation(relations.get(0), "One Two Three Four", "LastName", "FTH");
    }

    @Test
    public void shouldNotAddRelationsIfNotPresent() throws Exception {
        org.openmrs.module.shrclient.model.Patient orphanPatient = getPatientFromJson("patients_response/by_hid.json");
        Patient emrPatient = new Patient();
        new RelationshipMapper().addRelationAttributes(orphanPatient.getRelations(), emrPatient, idMappingsRepository);
        assertEquals(0, emrPatient.getAttributes().size());
    }

    @Test
    public void shouldAddOnlyFatherAndSpouseRelations() throws Exception {
        org.openmrs.module.shrclient.model.Patient patientWithOtherRelations = getPatientFromJson("patients_response/patientWithRelations.json");
        Patient emrPatient = new Patient();
        new RelationshipMapper().addRelationAttributes(patientWithOtherRelations.getRelations(), emrPatient, idMappingsRepository);
        assertEquals(3, emrPatient.getAttributes().size());

        PersonAttribute father = (PersonAttribute) CollectionUtils.find(emrPatient.getAttributes(),
                new BeanPropertyValueEqualsPredicate("attributeType.name", OpenMRSConstants.FATHER_NAME_ATTRIBUTE_TYPE));
        assertEquals("Md. Sakib Ali Khan", father.getValue());

        PersonAttribute spouse = (PersonAttribute) CollectionUtils.find(emrPatient.getAttributes(),
                new BeanPropertyValueEqualsPredicate("attributeType.name", OpenMRSConstants.SPOUSE_NAME_ATTRIBUTE_TYPE));
        assertEquals("Azad", spouse.getValue());

        PersonAttribute mother = (PersonAttribute) CollectionUtils.find(emrPatient.getAttributes(),
                new BeanPropertyValueEqualsPredicate("attributeType.name", OpenMRSConstants.MOTHER_NAME_ATTRIBUTE_TYPE));
        assertEquals("Shabnam Chowdhury", mother.getValue());
    }

    @Test
    public void shouldAddOnlyRelationPresent() throws Exception {
        org.openmrs.module.shrclient.model.Patient patientWithOtherRelations = getPatientFromJson("patients_response/patientWithFather.json");
        Patient emrPatient = new Patient();

        new RelationshipMapper().addRelationAttributes(patientWithOtherRelations.getRelations(), emrPatient, idMappingsRepository);

        assertEquals(1, emrPatient.getAttributes().size());

        PersonAttribute father = (PersonAttribute) CollectionUtils.find(emrPatient.getAttributes(), new BeanPropertyValueEqualsPredicate("attributeType.name", OpenMRSConstants.FATHER_NAME_ATTRIBUTE_TYPE));
        assertEquals("Md. Sakib Ali Khan", father.getValue());

        PersonAttribute spouse = (PersonAttribute) CollectionUtils.find(emrPatient.getAttributes(), new BeanPropertyValueEqualsPredicate("attributeType.name", OpenMRSConstants.SPOUSE_NAME_ATTRIBUTE_TYPE));
        assertNull(spouse);
    }

    private PersonAttributeType getAttributeType(String attributeType) {
        PersonAttributeType type = new PersonAttributeType();
        type.setName(attributeType);
        return type;
    }

    private void assertRelation(Relation relation, String givenName, String surName, String type) {
        assertEquals(givenName, relation.getGivenName());
        assertEquals(surName, relation.getSurName());
        assertEquals(type, relation.getType());
        assertNotNull(relation.getId());
    }

    private org.openmrs.module.shrclient.model.Patient getPatientFromJson(String patientJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        URL resource = URLClassLoader.getSystemResource(patientJson);
        final String patientResponse = FileUtils.readFileToString(new File(resource.getPath()));

        return mapper.readValue(patientResponse, org.openmrs.module.shrclient.model.Patient.class);
    }

    private void createOpenMrsPersonAttributes(Patient patient, Map<String, String> attributeValueMap) {
        for (String attributeTypeName : attributeValueMap.keySet()) {
            PersonAttributeType attributeType = getAttributeType(attributeTypeName);
            patient.addAttribute(new PersonAttribute(attributeType, attributeValueMap.get(attributeTypeName)));
        }
    }

    private void mockPersonServiceInContext() {
        Context context = new Context();
        ServiceContext serviceContext = ServiceContext.getInstance();
        serviceContext.setService(PersonService.class, personService);
        context.setServiceContext(serviceContext);
    }

    private List<Relation> filterRelationsWithNames(List<Relation> relations) {
        List<Relation> relationsWithName = new ArrayList<>();
        for (Relation relation : relations) {
            if (StringUtils.isNotBlank(relation.getGivenName()) || StringUtils.isNotBlank(relation.getSurName()))
                relationsWithName.add(relation);
        }
        return relationsWithName;
    }
}