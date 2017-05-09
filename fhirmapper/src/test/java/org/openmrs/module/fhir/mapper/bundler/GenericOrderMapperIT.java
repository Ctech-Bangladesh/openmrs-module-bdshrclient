package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.FHIRProperties.DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GenericOrderMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private GenericOrderMapper genericOrderMapper;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private OrderService orderService;

    private final String patientRef = "http://mci.com/patients/HID-123";
    private final String fhirEncounterId = "SHR-ENC1";

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleRadiologyOrder() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        Order order = orderService.getOrder(16);
        assertTrue(genericOrderMapper.canHandle(order));
    }

    @Test
    public void shouldNotHandleTestOrder() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        Order order = orderService.getOrder(20);
        assertFalse(genericOrderMapper.canHandle(order));
    }

    @Test
    public void shouldNotHandleDrugOrder() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
        Order order = orderService.getOrder(77);
        assertFalse(genericOrderMapper.canHandle(order));
    }

    @Test
    public void shouldMapALocalRadiologyOrder() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        Order order = orderService.getOrder(16);
        List<FHIRResource> mappedResources = genericOrderMapper.map(order, createFhirEncounter(), new Bundle(), getSystemProperties("1"));
        assertTrue(CollectionUtils.isNotEmpty(mappedResources));
        ProcedureRequest diagnosticOrder = (ProcedureRequest) mappedResources.get(0).getResource();
        assertProcedureRequest(diagnosticOrder, order.getUuid());
//        assertEquals(1, diagnosticOrder.getItem().size());
//        ProcedureRequest.Item item = diagnosticOrder.getItemFirstRep();
//        assertTrue(MapperTestHelper.containsCoding(item.getCode().getCoding(), null, null, "X-ray left hand"));
//        assertEquals(1, item.getEvent().size());
//        assertTrue(hasEventWithDateTime(item, ProcedureRequestStatusEnum.REQUESTED, order.getDateActivated()));
    }

    @Test
    public void shouldMapTRRadiologyOrder() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        Encounter encounter = encounterService.getEncounter(37);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();
        List<FHIRResource> mappedResources = genericOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));
        assertNotNull(mappedResources);
        assertEquals(1, mappedResources.size());
        ProcedureRequest diagnosticOrder = (ProcedureRequest) TestFhirFeedHelper.getFirstResourceByType(new ProcedureRequest().getResourceType().name(), mappedResources).getResource();
        assertNotNull(diagnosticOrder);
//        assertTrue(diagnosticOrder.getOrderer().getReference().getValue().endsWith("812.json"));
//        assertEquals(1, diagnosticOrder.getItem().size());
//        assertTrue(MapperTestHelper.containsCoding(diagnosticOrder.getItemFirstRep().getCode().getCoding(), "501qb827-a67c-4q1f-a705-e5efe0q6a972",
//                "http://localhost:9997/openmrs/ws/rest/v1/tr/concept/501qb827-a67c-4q1f-a705-e5efe0q6a972", "X-Ray Right Chest"));
    }

    @Test
    public void shouldNotMapAStoppedTestOrder() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(18);
        List<FHIRResource> fhirResources = genericOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertTrue(fhirResources.isEmpty());
    }

    @Test
    public void shouldMapADiscountinuedOrder() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(19);
        List<FHIRResource> fhirResources = genericOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertEquals(1, fhirResources.size());
        ProcedureRequest diagnosticOrder = (ProcedureRequest) fhirResources.get(0).getResource();
        assertProcedureRequest(diagnosticOrder, order.getPreviousOrder().getUuid());
        assertProvenance(fhirResources, diagnosticOrder);
//        List<ProcedureRequest.Item> items = diagnosticOrder.getItem();
//        assertEquals(1, items.size());
//        ProcedureRequest.Item item = items.get(0);
//        assertEquals(ProcedureRequestStatusEnum.CANCELLED.getCode(), item.getStatus());
//        assertEquals(2, item.getEvent().size());
//        assertTrue(hasEventWithDateTime(item, ProcedureRequestStatusEnum.CANCELLED, order.getDateActivated()));
//        assertTrue(hasEventWithDateTime(item, ProcedureRequestStatusEnum.REQUESTED, order.getPreviousOrder().getDateActivated()));
    }

//    private boolean hasEventWithDateTime(ProcedureRequest.Item item, ProcedureRequestStatusEnum status, Date datetime) {
//        for (ProcedureRequest.Event event : item.getEvent()) {
//            if (event.getStatus().equals(status.getCode()) && event.getDateTime().equals(datetime)) return true;
//        }
//        return false;
//    }

    private void assertProcedureRequest(ProcedureRequest diagnosticOrder, String orderId) {
        assertEquals(patientRef, diagnosticOrder.getSubject().getReference());
//        assertTrue(diagnosticOrder.getOrderer().getReference().getValue().endsWith("812.json"));
        orderId = "urn:uuid:" + orderId;
        assertEquals(orderId, diagnosticOrder.getId());
        assertEquals(1, diagnosticOrder.getIdentifier().size());
        assertEquals(orderId, diagnosticOrder.getIdentifierFirstRep().getValue());
        assertFalse(diagnosticOrder.getIdentifier().get(0).isEmpty());
        assertEquals(fhirEncounterId, diagnosticOrder.getContext().getReference());
        assertEquals(1, diagnosticOrder.getExtension().size());
        Extension extensionDt = diagnosticOrder.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME)).get(0);
        assertTrue(extensionDt.getValue() instanceof StringType);
        assertEquals(FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE, ((StringType) extensionDt.getValue()).getValue());

    }

    private FHIREncounter createFhirEncounter() {

        org.hl7.fhir.dstu3.model.Encounter encounter = new org.hl7.fhir.dstu3.model.Encounter();
        encounter.setSubject(new Reference(patientRef));
        encounter.setId(fhirEncounterId);
        return new FHIREncounter(encounter);
    }

    private void assertProvenance(List<FHIRResource> mappedResources, ProcedureRequest procedureRequest) {
        Provenance provenance = (Provenance) mappedResources.get(1).getResource();
        assertTrue(((Reference) provenance.getAgent().get(0).getWho()).getReference().endsWith("812.json"));
        assertEquals(provenance.getTargetFirstRep().getReference(), procedureRequest.getId());
    }
}