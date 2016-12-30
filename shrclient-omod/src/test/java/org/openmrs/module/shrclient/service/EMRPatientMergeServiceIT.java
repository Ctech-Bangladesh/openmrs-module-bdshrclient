package org.openmrs.module.shrclient.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir.OpenMRSConstants;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.OpenMRSConstants.*;
import static org.openmrs.module.fhir.OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE_NAME;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EMRPatientMergeServiceIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private PatientService patientService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    EMRPatientService emrPatientService;
    @Autowired
    EMRPatientMergeService emrPatientMergeService;
    @Autowired
    IdMappingRepository idMappingRepository;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/attributeTypesDS.xml");
        executeDataSet("testDataSets/dhakaAddressHierarchy.xml");
    }

    @Test
    public void shouldMergePatientsWithHIDAsIdentifiers() throws Exception {
        executeDataSet("testDataSets/mergeDS/patientMergeDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retiredHID = patientToBeRetired.getHealthId();
        String retainedHID = patientToBeRetained.getHealthId();

        Patient emrPatientTobeRetired = emrPatientService.getEMRPatientByHealthId(retiredHID);
        Patient emrPatientToBeRetained = emrPatientService.getEMRPatientByHealthId(retainedHID);

        List<PatientIdentifier> toBeRetainedIdentifiers = emrPatientToBeRetained.getActiveIdentifiers();
        assertEquals(2, toBeRetainedIdentifiers.size());
        List<PatientIdentifier> toBeRetiredIdentifiers = emrPatientTobeRetired.getActiveIdentifiers();
        assertEquals(2, toBeRetiredIdentifiers.size());

        emrPatientMergeService.mergePatients(retainedHID, retiredHID);

        Patient retainedPatient = emrPatientService.getEMRPatientByHealthId(retainedHID);
        List<PatientIdentifier> retainedPatientActiveIdentifiers = retainedPatient.getActiveIdentifiers();
        assertEquals(2, retainedPatientActiveIdentifiers.size());
        assertEquals(retainedHID, retainedPatient.getPatientIdentifier(HEALTH_ID_IDENTIFIER_TYPE_NAME).getIdentifier());

        Patient retiredPatient = emrPatientService.getEMRPatientByHealthId(retiredHID);
        assertTrue(retiredPatient.isVoided());
        List<PatientIdentifier> retiredPatientActiveIdentifiers = retiredPatient.getActiveIdentifiers();
        assertEquals(0, retiredPatientActiveIdentifiers.size());
        assertNull(retiredPatient.getPatientIdentifier(HEALTH_ID_IDENTIFIER_TYPE_NAME));
    }

    @Test
    public void shouldMergePatientAttributesAndVoidToBeRetiredPatient() throws Exception {
        executeDataSet("testDataSets/mergeDS/patientMergeDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retainedHealthId = patientToBeRetained.getHealthId();

        emrPatientMergeService.mergePatients(retainedHealthId, patientToBeRetired.getHealthId());

        Patient retiredPatient = patientService.getPatient(11);
        Patient retainedPatient = patientService.getPatient(21);
        String expectedVoidReason = String.format("Merged with patient #%s", "21");

        assertTrue(retiredPatient.getVoided());
        assertEquals(expectedVoidReason, retiredPatient.getVoidReason());

        assertTrue(retiredPatient.getPerson().getPersonAddress().getVoided());
        assertEquals(expectedVoidReason, retiredPatient.getPerson().getPersonAddress().getVoidReason());

        assertEquals(0, retiredPatient.getActiveIdentifiers().size());
        assertEquals(9, retiredPatient.getAttributes().size());
        assertEquals(expectedVoidReason, retiredPatient.getAttributes().iterator().next().getVoidReason());

        assertEquals(0, retiredPatient.getActiveAttributes().size());
        assertEquals(1, retiredPatient.getNames().size());
        PersonName retiredPatientName = retiredPatient.getNames().iterator().next();
        assertTrue(retiredPatientName.getVoided());
        assertEquals(expectedVoidReason, retiredPatientName.getVoidReason());

        assertFalse(retainedPatient.getVoided());
        assertEquals(retainedHealthId, retainedPatient.getAttribute(HEALTH_ID_ATTRIBUTE).getValue());
        assertEquals("7654376543777", retainedPatient.getAttribute(NATIONAL_ID_ATTRIBUTE).getValue());
        assertEquals("54098599985409999", retainedPatient.getAttribute(BIRTH_REG_NO_ATTRIBUTE).getValue());
        assertEquals("121", retainedPatient.getAttribute(HOUSE_HOLD_CODE_ATTRIBUTE).getValue());
        assertEquals("123", retainedPatient.getAttribute(PHONE_NUMBER).getValue());
        assertEquals("F", retainedPatient.getGender());

        assertEquals(2, retainedPatient.getNames().size());
        assertEquals(8, retainedPatient.getActiveAttributes().size());
        Iterator<PersonName> personNameIterator = retainedPatient.getNames().iterator();
        assertEquals("Abdul", retainedPatient.getGivenName());
        assertEquals("Khan", retainedPatient.getFamilyName());
        String expectedVoidMessageForUnPreferred = "Merged from patient #11";
        personNameIterator.next();
        PersonName nameFromRetiredPatient = personNameIterator.next();
        assertTrue(nameFromRetiredPatient.getVoided());
        assertEquals(expectedVoidMessageForUnPreferred, nameFromRetiredPatient.getVoidReason());

        assertEquals("Rahman Khan", retainedPatient.getAttribute(FATHER_NAME_ATTRIBUTE_TYPE).getValue());
        assertEquals("Bismillah", retainedPatient.getAttribute(SPOUSE_NAME_ATTRIBUTE_TYPE).getValue());

        assertEquals(2, retainedPatient.getAddresses().size());
        Iterator<PersonAddress> addressIterator = retainedPatient.getAddresses().iterator();
        PersonAddress retainedPatientAddress = addressIterator.next();
        assertFalse(retainedPatientAddress.getVoided());
        assertTrue(retainedPatientAddress.getPreferred());
        assertEquals("Dhaka", retainedPatientAddress.getStateProvince());
        assertEquals("Dhaka", retainedPatientAddress.getCountyDistrict());
        assertEquals("Adabor", retainedPatientAddress.getAddress5());
        assertEquals("Dhaka Uttar City Corp.", retainedPatientAddress.getAddress4());
        assertEquals("Urban Ward No-30 (43)", retainedPatientAddress.getAddress3());
        assertEquals("house", retainedPatientAddress.getAddress1());

        PersonAddress retainedPatientAddress2 = addressIterator.next();
        assertFalse(retainedPatientAddress2.getPreferred());
        assertTrue(retainedPatientAddress2.getVoided());
        assertEquals(expectedVoidMessageForUnPreferred, retainedPatientAddress2.getVoidReason());
    }

    @Test
    public void shouldMergeEncountersWhenPatientsAreMerged() throws Exception {
        executeDataSet("testDataSets/mergeDS/patientMergeDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retainedHealthId = patientToBeRetained.getHealthId();

        List<Encounter> encountersOfToBeRetiredPatient = encounterService.getEncountersByPatientId(11);
        List<Encounter> encountersOfToBeRetainedPatient = encounterService.getEncountersByPatientId(21);

        Integer visitIdOfRetiredPatient = encountersOfToBeRetiredPatient.get(0).getVisit().getId();
        Integer visitIdOfRetainedPatient = encountersOfToBeRetainedPatient.get(0).getVisit().getId();
        Date startDatetimeForVisitOfToBeRetiredPatient = encountersOfToBeRetiredPatient.get(0).getVisit().getStartDatetime();
        Date stopDatetimeForVisitOfToBeRetiredPatient = encountersOfToBeRetiredPatient.get(0).getVisit().getStopDatetime();
        Date startDatetimeForVisitOfToBeRetainedPatient = encountersOfToBeRetainedPatient.get(0).getVisit().getStartDatetime();
        Date stopDatetimeForVisitOfToBeRetainedPatient = encountersOfToBeRetainedPatient.get(0).getVisit().getStopDatetime();


        assertEquals(1, encountersOfToBeRetiredPatient.size());
        assertEquals(2, encountersOfToBeRetiredPatient.get(0).getOrders().size());
        assertEquals(4, encountersOfToBeRetiredPatient.get(0).getAllObs(true).size());
        assertEquals(1, encountersOfToBeRetainedPatient.size());

        emrPatientMergeService.mergePatients(retainedHealthId, patientToBeRetired.getHealthId());

        List<Encounter> encountersOfRetainedPatients = encounterService.getEncountersByPatientId(21);
        Encounter mergedEncounterOne = encountersOfRetainedPatients.get(0);
        Encounter mergedEncounterTwo = encountersOfRetainedPatients.get(1);

        assertEquals(0, encounterService.getEncountersByPatientId(11).size());
        assertEquals(2, encountersOfRetainedPatients.size());
        assertEquals(2, mergedEncounterOne.getOrders().size());
        assertEquals(4, mergedEncounterOne.getAllObs(true).size());

        assertEquals(visitIdOfRetiredPatient, mergedEncounterOne.getVisit().getId());
        assertEquals(visitIdOfRetainedPatient, mergedEncounterTwo.getVisit().getId());
        assertEquals(startDatetimeForVisitOfToBeRetiredPatient, mergedEncounterOne.getVisit().getStartDatetime());
        assertEquals(stopDatetimeForVisitOfToBeRetiredPatient, mergedEncounterOne.getVisit().getStopDatetime());
        assertEquals(startDatetimeForVisitOfToBeRetainedPatient, mergedEncounterTwo.getVisit().getStartDatetime());
        assertEquals(stopDatetimeForVisitOfToBeRetainedPatient, mergedEncounterTwo.getVisit().getStopDatetime());

        Iterator<Order> orderIterator = mergedEncounterOne.getOrders().iterator();
        assertTrue(orderIterator.next().isVoided());
        assertFalse(orderIterator.next().isVoided());

    }

    @Test
    public void shouldCloseActiveVisitOfRetiredPatientIfRetainedPatientHasActiveVisit() throws Exception {
        executeDataSet("testDataSets/mergeDS/retainedPatientWithActiveVisitDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retainedHealthId = patientToBeRetained.getHealthId();

        List<Encounter> encountersOfToBeRetiredPatient = encounterService.getEncountersByPatientId(11);
        List<Encounter> encountersOfToBeRetainedPatient = encounterService.getEncountersByPatientId(21);

        Date stopDateOfActiveVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(0).getVisit().getStopDatetime();
        Date startDateOfActiveVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(0).getVisit().getStartDatetime();
        Date stopDateOfClosedVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(1).getVisit().getStopDatetime();
        Date startDateOfClosedVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(1).getVisit().getStartDatetime();
        Date stopDateOfActiveVisitOfRetainedPatientBeforeMerge = encountersOfToBeRetainedPatient.get(0).getVisit().getStopDatetime();
        Date startDateOfActiveVisitOfRetainedPatientBeforeMerge = encountersOfToBeRetainedPatient.get(0).getVisit().getStartDatetime();

        assertNull(stopDateOfActiveVisitOfRetiredPatientBeforeMerge);
        assertNull(stopDateOfActiveVisitOfRetainedPatientBeforeMerge);

        emrPatientMergeService.mergePatients(retainedHealthId, patientToBeRetired.getHealthId());

        List<Encounter> encountersOfToBeRetiredPatientAfterMerge = encounterService.getEncountersByPatientId(11);
        List<Encounter> encountersOfToBeRetainedPatientAfterMerge = encounterService.getEncountersByPatientId(21);

        assertEquals(0, encountersOfToBeRetiredPatientAfterMerge.size());
        assertEquals(3, encountersOfToBeRetainedPatientAfterMerge.size());

        assertEquals(startDateOfActiveVisitOfRetainedPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(0).getVisit().getStartDatetime());
        assertNull(encountersOfToBeRetainedPatientAfterMerge.get(0).getVisit().getStopDatetime());

        //active visit retired patient closes on merge if retained patient has active visit
        assertEquals(startDateOfActiveVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(1).getVisit().getStartDatetime());
        assertNotNull(encountersOfToBeRetainedPatientAfterMerge.get(1).getVisit().getStopDatetime());

        //closed visit times of retired patient donot change
        assertEquals(startDateOfClosedVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(2).getVisit().getStartDatetime());
        assertEquals(stopDateOfClosedVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(2).getVisit().getStopDatetime());
    }

    @Test
    public void shouldRetainActiveVisitOfRetiredPatientIfRetainedPatientHasNoActiveVisit() throws Exception {
        executeDataSet("testDataSets/mergeDS/retainedPatientWithNoActiveVisitDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retainedHealthId = patientToBeRetained.getHealthId();

        List<Encounter> encountersOfToBeRetiredPatient = encounterService.getEncountersByPatientId(11);
        List<Encounter> encountersOfToBeRetainedPatient = encounterService.getEncountersByPatientId(21);

        Date stopDateOfActiveVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(0).getVisit().getStopDatetime();
        Date startDateOfActiveVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(0).getVisit().getStartDatetime();
        Date stopDateOfClosedVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(1).getVisit().getStopDatetime();
        Date startDateOfClosedVisitOfRetiredPatientBeforeMerge = encountersOfToBeRetiredPatient.get(1).getVisit().getStartDatetime();
        Date stopDateOfClosedVisitOfRetainedPatientBeforeMerge = encountersOfToBeRetainedPatient.get(0).getVisit().getStopDatetime();
        Date startDateOfClosedVisitOfRetainedPatientBeforeMerge = encountersOfToBeRetainedPatient.get(0).getVisit().getStartDatetime();

        assertNull(stopDateOfActiveVisitOfRetiredPatientBeforeMerge);
        assertNotNull(stopDateOfClosedVisitOfRetainedPatientBeforeMerge);

        emrPatientMergeService.mergePatients(retainedHealthId, patientToBeRetired.getHealthId());

        List<Encounter> encountersOfToBeRetiredPatientAfterMerge = encounterService.getEncountersByPatientId(11);
        List<Encounter> encountersOfToBeRetainedPatientAfterMerge = encounterService.getEncountersByPatientId(21);

        assertEquals(0, encountersOfToBeRetiredPatientAfterMerge.size());
        assertEquals(3, encountersOfToBeRetainedPatientAfterMerge.size());

        assertEquals(startDateOfClosedVisitOfRetainedPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(0).getVisit().getStartDatetime());
        assertEquals(stopDateOfClosedVisitOfRetainedPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(0).getVisit().getStopDatetime());

        //active visit retired patient remains active on merge if retained patient has no active visit
        assertEquals(startDateOfActiveVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(1).getVisit().getStartDatetime());
        assertNull(encountersOfToBeRetainedPatientAfterMerge.get(1).getVisit().getStopDatetime());

        //closed visit times of retired patient donot change
        assertEquals(startDateOfClosedVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(2).getVisit().getStartDatetime());
        assertEquals(stopDateOfClosedVisitOfRetiredPatientBeforeMerge, encountersOfToBeRetainedPatientAfterMerge.get(2).getVisit().getStopDatetime());

    }

    @Test
    public void shouldUpdateIDmappingsAfterMerge() throws Exception {
        executeDataSet("testDataSets/mergeDS/patientMergeDS.xml");
        org.openmrs.module.shrclient.model.Patient patientToBeRetired = getPatientFromJson("patients_response/patientWithRelations.json");
        org.openmrs.module.shrclient.model.Patient patientToBeRetained = getPatientFromJson("patients_response/p12341467785.json");

        emrPatientService.createOrUpdateEmrPatient(patientToBeRetained);
        emrPatientService.createOrUpdateEmrPatient(patientToBeRetired);
        String retainedHealthId = patientToBeRetained.getHealthId();
        String retiredHealthId = patientToBeRetired.getHealthId();

        assertEquals(1, idMappingRepository.findByHealthId(retainedHealthId, IdMappingType.ENCOUNTER).size());
        assertEquals(1, idMappingRepository.findByHealthId(retiredHealthId, IdMappingType.ENCOUNTER).size());
        assertEquals(1, idMappingRepository.findByHealthId(retiredHealthId, IdMappingType.MEDICATION_ORDER).size());
        assertEquals(0, idMappingRepository.findByHealthId(retainedHealthId, IdMappingType.MEDICATION_ORDER).size());

        emrPatientMergeService.mergePatients(retainedHealthId, patientToBeRetired.getHealthId());

        assertEquals(2, idMappingRepository.findByHealthId(retainedHealthId, IdMappingType.ENCOUNTER).size());
        assertEquals(0, idMappingRepository.findByHealthId(retiredHealthId, IdMappingType.ENCOUNTER).size());
        assertEquals(1, idMappingRepository.findByHealthId(retiredHealthId, IdMappingType.PATIENT).size());
        assertEquals(1, idMappingRepository.findByHealthId(retainedHealthId, IdMappingType.PATIENT).size());
        assertEquals(0, idMappingRepository.findByHealthId(retiredHealthId, IdMappingType.MEDICATION_ORDER).size());
        assertEquals(1, idMappingRepository.findByHealthId(retainedHealthId, IdMappingType.MEDICATION_ORDER).size());
    }

    private org.openmrs.module.shrclient.model.Patient getPatientFromJson(String patientJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        URL resource = URLClassLoader.getSystemResource(patientJson);
        final String patientResponse = FileUtils.readFileToString(new File(resource.getPath()));

        return mapper.readValue(patientResponse, org.openmrs.module.shrclient.model.Patient.class);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}
