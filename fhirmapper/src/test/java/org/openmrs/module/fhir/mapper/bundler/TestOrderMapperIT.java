package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticOrder;
import ca.uhn.fhir.model.dstu2.valueset.DiagnosticOrderStatusEnum;
import ca.uhn.fhir.model.primitive.StringDt;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.fhir.FHIRProperties.DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.getFhirExtensionUrl;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TestOrderMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private TestOrderMapper testOrderMapper;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private OrderService orderService;
    
    private final String patientRef = "http://mci.com/patients/HID-123";
    private final String fhirEncounterId = "SHR-ENC1";

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapALocalTestOrder() throws Exception {
        Order order = orderService.getOrder(17);
        List<FHIRResource> mappedResources = testOrderMapper.map(order, createFhirEncounter(), new Bundle(), getSystemProperties("1"));
        assertTrue(CollectionUtils.isNotEmpty(mappedResources));
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) mappedResources.get(0).getResource();
        assertDiagnosticOrder(diagnosticOrder, order.getUuid());
        assertEquals(1, diagnosticOrder.getItem().size());
        DiagnosticOrder.Item item = diagnosticOrder.getItemFirstRep();
        assertTrue(MapperTestHelper.containsCoding(item.getCode().getCoding(), null, null, "Urea Nitorgen"));
        assertEquals(1, item.getEvent().size());
        assertTrue(hasEventWithDateTime(item, DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated()));
    }

    @Test
    public void shouldMapTestOrderWithTRRefterm() throws Exception {
        Encounter encounter = encounterService.getEncounter(36);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();
        List<FHIRResource> mappedResources = testOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));
        assertNotNull(mappedResources);
        assertEquals(1, mappedResources.size());
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) TestFhirFeedHelper.getFirstResourceByType(new DiagnosticOrder().getResourceName(), mappedResources).getResource();
        assertNotNull(diagnosticOrder);
        assertTrue(diagnosticOrder.getOrderer().getReference().getValue().endsWith("812.json"));
        assertEquals(1, diagnosticOrder.getItem().size());
        assertTrue(MapperTestHelper.containsCoding(diagnosticOrder.getItemFirstRep().getCode().getCoding(), "20563-3",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a705-e5efe0q6a972", "Haemoglobin"));
    }

    @Test
    public void shouldMapPanelOrderWithTRConcept() throws Exception {
        Encounter encounter = encounterService.getEncounter(39);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();
        List<FHIRResource> mappedResources = testOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));
        assertEquals(1, mappedResources.size());
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) TestFhirFeedHelper.getFirstResourceByType(new DiagnosticOrder().getResourceName(), mappedResources).getResource();
        assertDiagnosticOrder(diagnosticOrder, order.getUuid());
        assertEquals(1, diagnosticOrder.getItem().size());
        assertTrue(MapperTestHelper.containsCoding(diagnosticOrder.getItemFirstRep().getCode().getCoding(), "30xlb827-s02l-4q1f-a705-e5efe0qjki2w",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concepts/30xlb827-s02l-4q1f-a705-e5efe0qjki2w", "Complete Blood Count"));
    }

    @Test
    public void shouldMapLocalPanelOrder() throws Exception {
        Encounter encounter = encounterService.getEncounter(40);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();
        List<FHIRResource> mappedResources = testOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));
        assertEquals(1, mappedResources.size());
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) TestFhirFeedHelper.getFirstResourceByType(new DiagnosticOrder().getResourceName(), mappedResources).getResource();
        assertDiagnosticOrder(diagnosticOrder, order.getUuid());
        assertEquals(3, diagnosticOrder.getItem().size());
        assertTrue(containsItem(diagnosticOrder.getItem(), DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated(), "Haemoglobin", "20563-3",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a705-e5efe0q6a972"));
        assertTrue(containsItem(diagnosticOrder.getItem(), DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated(), "ESR", "20563-4",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a714-e5efe0qjki2w"));
        assertTrue(containsItem(diagnosticOrder.getItem(), DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated(), "Hb Electrophoresis", null, null));
    }

    @Test
    public void shouldNotMapAStoppedTestOrder() throws Exception {
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(24);
        List<FHIRResource> fhirResources = testOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertTrue(fhirResources.isEmpty());
    }

    @Test
    public void shouldMapADiscountinuedOrder() throws Exception {
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(25);
        List<FHIRResource> fhirResources = testOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertEquals(1, fhirResources.size());
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) fhirResources.get(0).getResource();
        assertDiagnosticOrder(diagnosticOrder, order.getPreviousOrder().getUuid());
        List<DiagnosticOrder.Item> items = diagnosticOrder.getItem();
        assertEquals(1, items.size());
        DiagnosticOrder.Item item = items.get(0);
        assertEquals(DiagnosticOrderStatusEnum.CANCELLED.getCode(), item.getStatus());
        assertEquals(2, item.getEvent().size());
        assertTrue(hasEventWithDateTime(item, DiagnosticOrderStatusEnum.CANCELLED, order.getDateActivated()));
        assertTrue(hasEventWithDateTime(item, DiagnosticOrderStatusEnum.REQUESTED, order.getPreviousOrder().getDateActivated()));
    }

    private boolean hasEventWithDateTime(DiagnosticOrder.Item item, DiagnosticOrderStatusEnum status, Date datetime) {
        for (DiagnosticOrder.Event event : item.getEvent()) {
            if(event.getStatus().equals(status.getCode()) && event.getDateTime().equals(datetime)) return true;
        }
        return false;
    }

    private boolean containsItem(List<DiagnosticOrder.Item> items, DiagnosticOrderStatusEnum orderStatus, Date dateTime, String display, String code, String system) {
        for (DiagnosticOrder.Item item : items) {
            if (item.getStatus().equals(orderStatus.getCode()) && hasEventWithDateTime(item, orderStatus, dateTime)
                    && MapperTestHelper.containsCoding(item.getCode().getCoding(), code, system, display)) return true;
        }
        return false;
    }

    private void assertDiagnosticOrder(DiagnosticOrder diagnosticOrder, String orderId) {
        assertEquals(patientRef, diagnosticOrder.getSubject().getReference().getValue());
        assertTrue(diagnosticOrder.getOrderer().getReference().getValue().endsWith("812.json"));
        orderId = "urn:uuid:" + orderId;
        assertEquals(orderId, diagnosticOrder.getId().getValue());
        assertEquals(1, diagnosticOrder.getIdentifier().size());
        assertEquals(orderId, diagnosticOrder.getIdentifierFirstRep().getValue());
        assertFalse(diagnosticOrder.getIdentifier().get(0).isEmpty());
        assertEquals(fhirEncounterId, diagnosticOrder.getEncounter().getReference().getValue());
        String fhirExtensionUrl = getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME);
        List<ExtensionDt> extensions = diagnosticOrder.getUndeclaredExtensionsByUrl(fhirExtensionUrl);
        assertEquals(1, extensions.size());
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE, ((StringDt) extensions.get(0).getValue()).getValue());
    }

    private FHIREncounter createFhirEncounter() {
        ca.uhn.fhir.model.dstu2.resource.Encounter encounter = new ca.uhn.fhir.model.dstu2.resource.Encounter();
        encounter.setPatient(new ResourceReferenceDt(patientRef));
        encounter.setId(fhirEncounterId);
        return new FHIREncounter(encounter);
    }
}