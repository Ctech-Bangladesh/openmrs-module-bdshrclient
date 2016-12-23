package org.openmrs.module.shrclient.service;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import com.sun.syndication.feed.atom.Category;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.api.*;
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
    private EMRPatientService emrPatientService;
    @Autowired
    private PropertiesReader propertiesReader;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private VisitService visitService;
    @Autowired
    private FHIRMapper fhirMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private EMRPatientDeathService patientDeathService;
    @Autowired
    private EMRPatientMergeService emrPatientMergeService;
    @Autowired
    private VisitLookupService visitLookupService;
    @Autowired
    private SHREncounterEventService shrEncounterEventService;

    private EMREncounterService emrEncounterService;

    @Before
    public void setUp() throws Exception {
        emrEncounterService = new EMREncounterServiceImpl(emrPatientService, idMappingRepository, propertiesReader, systemUserService,
                visitService, fhirMapper, orderService, patientDeathService, emrPatientMergeService, visitLookupService, shrEncounterEventService);
        executeDataSet("testDataSets/omrsGlobalPropertyTestDS.xml");
    }

    @Test
    public void shouldSaveEncounter() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";
        List<EncounterEvent> encounterEvents = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/diagnosisConditions.xml");
        assertEquals(1, encounterEvents.size());
        ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter = (ca.uhn.fhir.model.dstu2.resource.Encounter) FHIRBundleHelper.identifyResourcesByName(encounterEvents.get(0).getBundle(),
                new ca.uhn.fhir.model.dstu2.resource.Encounter().getResourceName()).get(0);
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
        List<EncounterEvent> bundles = getEncounterEvents("shrEncounterId", "classpath:encounterBundles/dstu2/encounterWithDiagnosticOrder.xml");

        assertEquals(true, patient.isDead());
        assertEquals("Unspecified Cause Of Death", patient.getCauseOfDeath().getName().getName());

        emrEncounterService.createOrUpdateEncounter(patient, bundles.get(0));

        assertEquals(true, patient.isDead());
        assertEquals("HIV", patient.getCauseOfDeath().getName().getName());
    }

    @Test
    public void shouldSaveTestOrders() throws Exception {
        executeDataSet("testDataSets/shrDiagnosticOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithDiagnosticOrder.xml");
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
    public void shouldSaveTestOrdersWithoutOrder() throws Exception {
        executeDataSet("testDataSets/shrDiagnosticOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithDiagnosticReport.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(encounterIdMapping);
        Encounter encounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
        Set<Obs> allObs = encounter.getAllObs();
        assertEquals(1, allObs.size());
        assertNull(allObs.iterator().next().getOrder());
    }

    @Ignore
    @Test
    public void shouldDiscontinueATestOrderIfUpdated() throws Exception {
        executeDataSet("testDataSets/shrDiagnosticOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundleWithNewTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithDiagnosticOrder.xml");
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
        List<EncounterEvent> bundleWithCancelledTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithCancelledDiagnosticOrder.xml");

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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithProcedureRequest.xml");
        Patient emrPatient = patientService.getPatient(1);
        emrEncounterService.createOrUpdateEncounters(emrPatient, bundles);

        EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        assertNotNull(encounterIdMapping);
        Encounter encounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
        Set<Order> orders = encounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
    }

    @Ignore
    @Test
    public void shouldDiscontinueAProcedureOrderIfUpdated() throws Exception {
        executeDataSet("testDataSets/shrProcedureOrderSyncTestDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundleWithNewProcedureOrder = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithProcedureRequest.xml");
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
        List<EncounterEvent> bundleWithCancelledTestOrder = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithSuspendedProcedureRequest.xml");

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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithMedicationOrder.xml");
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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithStoppedMedicationOrderAndCustomDosage.xml");
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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithMedicationOrderWithScheduledDate.xml");
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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithMedicationOrderEditedInDifferentEncounter.xml");
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

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithMedicationOrderEditedInSameEncounter.xml");
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
    public void shouldSaveAMedicationOrderWithoutDoseRoutes() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/medicationOrderWithoutDoseRouteAndAdditionalInstructions.xml");
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
        List<EncounterEvent> events = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/diagnosisConditionsUpdate.xml");

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
        assertEquals(DateUtil.parseDate("2014-07-11 23:59:59"), finalVisit.getStopDatetime());
    }

    @Ignore
    @Test
    public void shouldVoidOlderObservationsAndRecreateWithNewValues() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterWithObservationTestDs.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id";

        List<EncounterEvent> bundles1 = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithObservations.xml");
        emrEncounterService.createOrUpdateEncounters(patient, bundles1);
        IdMapping mapping = idMappingRepository.findByExternalId(shrEncounterId, ENCOUNTER);
        Encounter encounter = encounterService.getEncounterByUuid(mapping.getInternalId());

        Set<Obs> topLevelObs = encounter.getObsAtTopLevel(true);
        assertEquals(1, topLevelObs.size());
        Obs diastolicBp = topLevelObs.iterator().next().getGroupMembers().iterator().next().getGroupMembers().iterator().next();
        assertEquals(new Double(70.0), diastolicBp.getValueNumeric());

        List<EncounterEvent> bundles2 = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/encounterWithUpdatedObservations.xml");
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

        EncounterEvent bundle1 = getEncounterEvents(shrEncounterId1, "encounterBundles/dstu2/medicationOrderWithPriorPrescription.xml").get(0);
        EncounterEvent bundle2 = getEncounterEvents(shrEncounterId2, "encounterBundles/dstu2/encounterWithMedicationOrder.xml").get(0);
        List<EncounterEvent> encounterEvents = asList(bundle1, bundle2);
        emrEncounterService.createOrUpdateEncounters(patient, encounterEvents);

        IdMapping mapping1 = idMappingRepository.findByExternalId(shrEncounterId1, ENCOUNTER);
        IdMapping mapping2 = idMappingRepository.findByExternalId(shrEncounterId2, ENCOUNTER);

        assertTrue(mapping1.getLastSyncDateTime().after(mapping2.getLastSyncDateTime()));
    }

    @Test
    public void shouldMapFhirConditions() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        Patient patient = patientService.getPatient(1);
        String shrEncounterId = "shr-enc-id1";

        EncounterEvent encounterEvent = getEncounterEvents(shrEncounterId, "encounterBundles/dstu2/diagnosisConditions.xml").get(0);

        List<IResource> conditions = FHIRBundleHelper.identifyResourcesByName(encounterEvent.getBundle(), new Condition().getResourceName());
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

    public IResource loadSampleFHIREncounter(String filePath, ApplicationContext springContext) throws Exception {
        org.springframework.core.io.Resource resource = springContext.getResource(filePath);
        String bundleXML = org.apache.commons.io.IOUtils.toString(resource.getInputStream());
        return (IResource) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}