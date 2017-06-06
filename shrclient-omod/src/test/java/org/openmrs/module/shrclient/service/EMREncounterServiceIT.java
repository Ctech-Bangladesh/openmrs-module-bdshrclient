package org.openmrs.module.shrclient.service;

import com.sun.syndication.feed.atom.Category;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Resource;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.mapper.emr.FHIRMapper;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.advice.SHREncounterEventService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.EncounterIdMapping;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.service.impl.EMREncounterServiceImpl;
import org.openmrs.module.shrclient.util.FhirBundleContextHolder;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.*;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.openmrs.module.shrclient.model.IdMappingType.ENCOUNTER;
import static org.openmrs.module.shrclient.model.IdMappingType.MEDICATION_ORDER;
import static org.openmrs.module.shrclient.web.controller.dto.EncounterEvent.ENCOUNTER_UPDATED_CATEGORY_TAG;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EMREncounterServiceIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private PatientService patientService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private IdMappingRepository idMappingRepository;
    @Autowired
    private VisitService visitService;
    @Autowired
    private EMREncounterService emrEncounterService;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/omrsGlobalPropertyTestDS.xml");
    }

    @Test
    public void shouldSaveEncounter() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";
        List<EncounterEvent> encounterEvents = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/diagnosisConditions.xml");
        assertEquals(1, encounterEvents.size());
        org.hl7.fhir.dstu3.model.Encounter fhirEncounter = (org.hl7.fhir.dstu3.model.Encounter) FHIRBundleHelper.identifyResourcesByName(encounterEvents.get(0).getBundle(),
                new org.hl7.fhir.dstu3.model.Encounter().getResourceType().name()).get(0);
        emrEncounterService.createOrUpdateEncounters(emrPatient, encounterEvents);

        EncounterIdMapping idMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        assertEquals(1, encounter.getEncounterProviders().size());
        assertEquals(providerService.getProvider(22), encounter.getEncounterProviders().iterator().next().getProvider());

        assertEquals(fhirEncounter.getType().get(0).getText(), encounter.getEncounterType().getName());
        assertNotNull(encounter.getEncounterProviders());
        assertEquals("Bahmni", encounter.getLocation().getName());

        Visit createdEncounterVisit = encounter.getVisit();
        assertNotNull(createdEncounterVisit);
        assertNotNull((createdEncounterVisit).getUuid());
        assertEquals("50ab30be-98af-4dfd-bd04-5455937c443f", encounter.getLocation().getUuid());
    }

    @Test
    public void shouldProcessDeathInfoOfPatientAfterEncounterSave() throws Exception {
        executeDataSet("testDataSets/patientDeathNoteDS.xml");

        Patient patient = patientService.getPatient(1);
        List<EncounterEvent> bundles = getEncounterEvents("shrEncounterId", "encounterBundles/stu3/encounterWithLabProcedureRequest.xml");

        assertEquals(true, patient.getDead());
        assertEquals("Unspecified Cause Of Death", patient.getCauseOfDeath().getName().getName());

        emrEncounterService.createOrUpdateEncounter(patient, bundles.get(0));

        assertEquals(true, patient.getDead());
        assertEquals("HIV", patient.getCauseOfDeath().getName().getName());
    }

    @Test
    public void shouldSaveTestOrders() throws Exception {
        executeDataSet("testDataSets/shrLabProcedureRequestSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithLabProcedureRequest.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(encounterIdMapping);
        Encounter encounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
    }

    @Test
    public void shouldSaveReportOrdersWithoutOrder() throws Exception {
        executeDataSet("testDataSets/shrLabProcedureRequestSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithDiagnosticReport.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(encounterIdMapping);
        Encounter encounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
        Set<Obs> allObs = encounter.getAllObs();
        assertEquals(1, allObs.size());
        assertNull(allObs.iterator().next().getOrder());
    }

    @Test
    @Ignore("Failing on ci")
    public void shouldDiscontinueATestOrderIfUpdated() throws Exception {
        executeDataSet("testDataSets/shrLabProcedureRequestSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundleWithNewTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithLabProcedureRequest.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundleWithNewTestOrder);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order firstOrder = orders.iterator().next();
        assertNull(firstOrder.getDateStopped());
        assertEquals(Order.Action.NEW, firstOrder.getAction());
        List<EncounterEvent> bundleWithCancelledTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithCanceledLabProcedureRequest.xml");

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundleWithCancelledTestOrder);

        IdMapping updatedIdMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(updatedIdMapping);
        assertTrue(updatedIdMapping.getLastSyncDateTime().after(idMapping.getLastSyncDateTime()));
        Encounter updatedEncounter = encounterService.getEncounterByUuid(updatedIdMapping.getInternalId());
        Set<Order> updatedEncounterOrders = updatedEncounter.getOrders();
        assertFalse(updatedEncounterOrders.isEmpty());
        assertEquals(2, updatedEncounterOrders.size());
        Order discontinuedOrder = getDiscontinuedOrder(updatedEncounterOrders);
        assertNotNull(discontinuedOrder);
        assertEquals(firstOrder, discontinuedOrder.getPreviousOrder());
        assertNotNull(firstOrder.getDateStopped());
    }

    @Test
    public void shouldSaveProcedureOrders() throws Exception {
        executeDataSet("testDataSets/shrProcedureOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithProcedureRequest.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(encounterIdMapping);
        Encounter encounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
    }

    @Test
    @Ignore("Failing on ci")
    public void shouldDiscontinueAProcedureOrderIfUpdated() throws Exception {
        executeDataSet("testDataSets/shrProcedureOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundleWithNewProcedureOrder = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithProcedureRequest.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundleWithNewProcedureOrder);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order firstOrder = orders.iterator().next();
        assertNull(firstOrder.getDateStopped());
        assertEquals(Order.Action.NEW, firstOrder.getAction());
        List<EncounterEvent> bundleWithCancelledTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithSuspendedProcedureRequest.xml");

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundleWithCancelledTestOrder);

        IdMapping updatedIdMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(updatedIdMapping);
        assertTrue(updatedIdMapping.getLastSyncDateTime().after(idMapping.getLastSyncDateTime()));
        Encounter updatedEncounter = encounterService.getEncounterByUuid(updatedIdMapping.getInternalId());
        Set<Order> updatedEncounterOrders = updatedEncounter.getOrders();
        assertFalse(updatedEncounterOrders.isEmpty());
        assertEquals(2, updatedEncounterOrders.size());
        Order discontinuedOrder = getDiscontinuedOrder(updatedEncounterOrders);
        assertNotNull(discontinuedOrder);
        assertEquals(firstOrder, discontinuedOrder.getPreviousOrder());
        assertNotNull(firstOrder.getDateStopped());
    }

    @Test
    public void shouldSaveDrugOrders() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithMedicationRequest.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldSaveDrugOrderWithCustomDosageAndStoppedOrder() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithStoppedMedicationRequestAndCustomDosage.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldSaveDrugOrderWithScheduledDate() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithMedicationRequestWithScheduledDate.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldSaveDrugOrderEditedInDifferentEncounter() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithMedicationRequestEditedInDifferentEncounter.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldSaveDrugOrderEditedInSameEncounter() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithMedicationRequestEditedInSameEncounter.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(2, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldSaveAMedicationRequestWithoutDoseRoutes() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/medicationRequestWithoutDoseRouteAndAdditionalInstructions.xml");
        Patient emrPatient = patientService.getPatient(110);

        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(idMapping);
        Encounter encounter = encounterService.getEncounterByUuid(idMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        assertTrue(orders.iterator().next() instanceof DrugOrder);
    }

    @Test
    public void shouldUpdateTheSameEncounterAndVisit() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";
        List<EncounterEvent> events = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/diagnosisConditionsUpdate.xml");

        Date currentTime = new Date();
        Date tenMinutesAfter = getDateTimeAfterNMinutes(currentTime, 10);

        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + DateUtil.toISOString(currentTime));
        events.get(0).setCategories(asList(category));

        Visit initialVisit = visitService.getVisit(1);
        String initialVisitUuid = initialVisit.getUuid();
        assertEquals(DateUtil.parseDate("2014-07-10 00:00:00"), initialVisit.getStartDatetime());
        assertEquals(DateUtil.parseDate("2014-07-11 23:59:59"), initialVisit.getStopDatetime());

        emrEncounterService.createOrUpdateEncounters(patient, events);
        EncounterIdMapping mapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        String encounterUUID = mapping.getInternalId();
        Date firstServerUpdateDateTime = mapping.getServerUpdateDateTime();

        Category newCategory = new Category();
        newCategory.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + DateUtil.toISOString(tenMinutesAfter));
        events.get(0).setCategories(asList(newCategory));

        emrEncounterService.createOrUpdateEncounters(patient, events);
        mapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        Encounter encounter = encounterService.getEncounterByUuid(mapping.getInternalId());

        assertEquals(initialVisitUuid, encounter.getVisit().getUuid());
        assertEquals(encounterUUID, encounter.getUuid());
        assertTrue(firstServerUpdateDateTime.before(mapping.getServerUpdateDateTime()));

        Visit finalVisit = visitService.getVisit(1);
        assertEquals(DateUtil.parseDate("2014-07-10 00:00:00"), finalVisit.getStartDatetime());
        assertEquals(DateUtil.parseDate("2014-07-27 16:05:09"), finalVisit.getStopDatetime());
    }

    @Test
    @Ignore("Failing on ci")
    public void shouldVoidOlderObservationsAndRecreateWithNewValues() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterWithObservationTestDs.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles1 = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithObservations.xml");
        emrEncounterService.createOrUpdateEncounters(patient, bundles1);
        IdMapping mapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        Encounter encounter = encounterService.getEncounterByUuid(mapping.getInternalId());

        Set<Obs> topLevelObs = encounter.getObsAtTopLevel(true);
        assertEquals(1, topLevelObs.size());
        Obs diastolicBp = topLevelObs.iterator().next().getGroupMembers().iterator().next().getGroupMembers().iterator().next();
        assertEquals(new Double(70.0), diastolicBp.getValueNumeric());

        List<EncounterEvent> bundles2 = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithUpdatedObservations.xml");
        emrEncounterService.createOrUpdateEncounters(patient, bundles2);
        mapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        encounter = encounterService.getEncounterByUuid(mapping.getInternalId());

        assertEquals(2, encounter.getObsAtTopLevel(true).size());
        topLevelObs = encounter.getObsAtTopLevel(false);
        assertEquals(1, topLevelObs.size());
        diastolicBp = topLevelObs.iterator().next().getGroupMembers().iterator().next().getGroupMembers().iterator().next();
        assertEquals(new Double(120.0), diastolicBp.getValueNumeric());
    }

    @Test
    public void shouldRetryIfFailsToDownloadAnEncounter() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        Patient patient = patientService.getPatient(110);
        String shrEncounterId1 = "shr-enc-id1";
        String shrEncounterId2 = "shr-enc-id2";

        EncounterEvent bundle1 = getEncounterEvents(shrEncounterId1, "encounterBundles/stu3/medicationRequestWithPriorPrescription.xml").get(0);
        EncounterEvent bundle2 = getEncounterEvents(shrEncounterId2, "encounterBundles/stu3/encounterWithMedicationRequest.xml").get(0);
        List<EncounterEvent> encounterEvents = asList(bundle1, bundle2);
        emrEncounterService.createOrUpdateEncounters(patient, encounterEvents);

        IdMapping mapping1 = idMappingRepository.findByExternalId(shrEncounterId1, ENCOUNTER);
        IdMapping mapping2 = idMappingRepository.findByExternalId(shrEncounterId2, ENCOUNTER);

        assertTrue(mapping1.getLastSyncDateTime().after(mapping2.getLastSyncDateTime()));
    }

    @Test(expected = org.openmrs.api.ValidationException.class)
    public void shouldPerformEventsAsTransactions() throws Exception {
        executeDataSet("testDataSets/drugOrderDSWithoutDrugRouts.xml");
        Patient patient = patientService.getPatient(110);
        String shrEncounterId1 = "shr-enc-id1";

        EncounterEvent bundle1 = getEncounterEvents(shrEncounterId1, "encounterBundles/stu3/encounterWithMedicationRequest.xml").get(0);
        emrEncounterService.createOrUpdateEncounters(patient, asList(bundle1));

        IdMapping mapping1 = idMappingRepository.findByExternalId(shrEncounterId1, ENCOUNTER);
        IdMapping orderIdMapping = idMappingRepository.findByExternalId(shrEncounterId1 + ":" + "7af48133-4c47-47d7-8d94-6a07abc18bf9", MEDICATION_ORDER);
        assertNull(mapping1);
        assertNull(orderIdMapping);
        assertNull(encounterService.getEncountersByPatient(patient).isEmpty());
    }

    @Test
    public void shouldMapFhirConditions() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id1";

        EncounterEvent encounterEvent = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/diagnosisConditions.xml").get(0);

        List<Resource> conditions = FHIRBundleHelper.identifyResourcesByName(encounterEvent.getBundle(), new Condition().getResourceType().name());
        assertEquals(2, conditions.size());

        emrEncounterService.createOrUpdateEncounter(patient, encounterEvent);

        IdMapping encounterMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterMapping);
        Encounter emrEncounter = encounterService.getEncounterByUuid(encounterMapping.getInternalId());

        Set<Obs> visitObs = emrEncounter.getObsAtTopLevel(false);
        assertEquals(2, visitObs.size());
        Obs firstObs = visitObs.iterator().next();
        assertNotNull(firstObs.getGroupMembers());
        assertNotNull(firstObs.getPerson());
        assertNotNull(firstObs.getEncounter());
    }

    @Test
    public void shouldCreateObsWithLocationIdOfSourceSystem() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterWithObservationTestDs.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";
        String facilityId = "10019841";

        List<EncounterEvent> bundles1 = getEncounterEvents(shrEncounterId, "encounterBundles/stu3/encounterWithObservations.xml");
        emrEncounterService.createOrUpdateEncounters(patient, bundles1);
        IdMapping mapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        Encounter encounter = encounterService.getEncounterByUuid(mapping.getInternalId());
        IdMapping frLocationMapping = idMappingRepository.findByExternalId(facilityId, IdMappingType.FACILITY);

        assertEquals(frLocationMapping.getInternalId(), encounter.getLocation().getUuid());
        for (Obs obs : encounter.getAllObs()) {
            assertEquals(frLocationMapping.getInternalId(), obs.getLocation().getUuid());
        }
    }


    private Order getDiscontinuedOrder(Set<Order> updatedEncounterOrders) {
        for (Order order : updatedEncounterOrders) {
            if (Order.Action.DISCONTINUE.equals(order.getAction())) return order;
        }
        return null;
    }

    public Date getDateTimeAfterNMinutes(Date currentTime, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

    private List<EncounterEvent> getEncounterEvents(String shrEncounterId, String encounterBundleFilePath) throws Exception {
        List<EncounterEvent> events = new ArrayList<>();
        EncounterEvent encounterEvent = new EncounterEvent();
        String publishedDate = DateUtil.toISOString(DateTime.now().toDate());
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + publishedDate);
        encounterEvent.setCategories(asList(category));
        encounterEvent.addContent((Bundle) loadSampleFHIREncounter(encounterBundleFilePath, springContext));
        encounterEvent.setTitle("Encounter:" + shrEncounterId);
        encounterEvent.setLink("http://shr.com/patients/" + encounterEvent.getHealthId() + "/encounters/" + shrEncounterId);
        events.add(encounterEvent);
        return events;
    }

    public Resource loadSampleFHIREncounter(String filePath, ApplicationContext springContext) throws Exception {
        org.springframework.core.io.Resource resource = springContext.getResource(filePath);
        String bundleXML = org.apache.commons.io.IOUtils.toString(resource.getInputStream());
        return (Resource) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}
