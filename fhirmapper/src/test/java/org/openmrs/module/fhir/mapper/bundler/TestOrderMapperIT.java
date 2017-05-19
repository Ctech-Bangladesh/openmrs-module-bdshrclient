package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.FHIRProperties.DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.getFhirExtensionUrl;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;
import static org.openmrs.module.fhir.TestFhirFeedHelper.getFirstResourceByType;

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
    public void shouldBeAbleToHandleLabTestOrders() throws Exception {
        Order order = orderService.getOrder(17);
        assertTrue(testOrderMapper.canHandle(order));
    }

    @Test
    public void shouldMapALocalTestOrder() throws Exception {
        Order order = orderService.getOrder(17);

        List<FHIRResource> mappedResources = testOrderMapper.map(order, createFhirEncounter(), new Bundle(), getSystemProperties("1"));

        assertTrue(CollectionUtils.isNotEmpty(mappedResources));
        assertEquals(2, mappedResources.size());
        ProcedureRequest procedureRequest = (ProcedureRequest) mappedResources.get(0).getResource();

        assertLabOrder(procedureRequest, order.getUuid() + "#1");
        assertProvenance(mappedResources, procedureRequest);

        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), null, null, "Urea Nitorgen"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, procedureRequest.getStatus());
        assertEquals(order.getDateActivated(), procedureRequest.getAuthoredOn());
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
        assertEquals(2, mappedResources.size());
        ProcedureRequest procedureRequest = (ProcedureRequest) getFirstResourceByType(new ProcedureRequest().getResourceType().name(), mappedResources).getResource();
        assertNotNull(procedureRequest);

        assertLabOrder(procedureRequest, order.getUuid() + "#123");
        assertProvenance(mappedResources, procedureRequest);

        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), "20563-3",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a705-e5efe0q6a972", "Haemoglobin"));
        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), "123",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concepts/123", "Haemoglobin"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, procedureRequest.getStatus());
        assertEquals(order.getDateActivated(), procedureRequest.getAuthoredOn());
    }

    @Test
    public void shouldMapPanelOrderWithTRConcept() throws Exception {
        String trConceptUuid = "30xlb827-s02l-4q1f-a705-e5efe0qjki2w";
        Encounter encounter = encounterService.getEncounter(39);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();

        List<FHIRResource> mappedResources = testOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));

        assertEquals(2, mappedResources.size());
        ProcedureRequest procedureRequest = (ProcedureRequest) getFirstResourceByType(new ProcedureRequest().getResourceType().name(), mappedResources).getResource();

        assertLabOrder(procedureRequest, order.getUuid() + "#" + trConceptUuid);
        assertProvenance(mappedResources, procedureRequest);

        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), trConceptUuid,
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concepts/30xlb827-s02l-4q1f-a705-e5efe0qjki2w", "Complete Blood Count"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, procedureRequest.getStatus());
        assertEquals(order.getDateActivated(), procedureRequest.getAuthoredOn());
    }

    @Test
    public void shouldMapLocalPanelOrder() throws Exception {
        String orderIdHemoglobine = "6glco326-eaab-4629-jsla-f1520ghkp0a0#123";
        String orderIdESR = "6glco326-eaab-4629-jsla-f1520ghkp0a0#124";
        String orderIdWBC = "6glco326-eaab-4629-jsla-f1520ghkp0a0#1";
        String orderIdHb = "6glco326-eaab-4629-jsla-f1520ghkp0a0#2";

        Encounter encounter = encounterService.getEncounter(40);
        FHIREncounter fhirEncounter = createFhirEncounter();
        assertEquals(1, encounter.getOrders().size());
        Bundle bundle = new Bundle();
        Order order = encounter.getOrders().iterator().next();

        List<FHIRResource> mappedResources = testOrderMapper.map(order, fhirEncounter, bundle, getSystemProperties("1"));
        assertEquals(8, mappedResources.size());

        ProcedureRequest hemoglobineRequest = (ProcedureRequest) getFhirResourceById(orderIdHemoglobine, mappedResources).getResource();
        assertLabOrder(hemoglobineRequest, orderIdHemoglobine);
        assertProvenance(mappedResources, hemoglobineRequest);
        assertTrue(containsCoding(hemoglobineRequest.getCode().getCoding(), "20563-3",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a705-e5efe0q6a972", "Haemoglobin"));
        assertTrue(containsCoding(hemoglobineRequest.getCode().getCoding(), "123",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concepts/123", "Haemoglobin"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, hemoglobineRequest.getStatus());
        assertEquals(order.getDateActivated(), hemoglobineRequest.getAuthoredOn());

        ProcedureRequest esrRequest = (ProcedureRequest) getFhirResourceById(orderIdESR, mappedResources).getResource();
        assertLabOrder(esrRequest, orderIdESR);
        assertProvenance(mappedResources, esrRequest);
        assertTrue(containsCoding(esrRequest.getCode().getCoding(), "20563-4",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/referenceterms/501qb827-a67c-4q1f-a714-e5efe0qjki2w", "ESR"));
        assertTrue(containsCoding(esrRequest.getCode().getCoding(), "124",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concepts/124", "ESR"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, esrRequest.getStatus());
        assertEquals(order.getDateActivated(), esrRequest.getAuthoredOn());

        ProcedureRequest wbcRequest = (ProcedureRequest) getFhirResourceById(orderIdWBC, mappedResources).getResource();
        assertLabOrder(wbcRequest, orderIdWBC);
        assertProvenance(mappedResources, wbcRequest);
        assertTrue(containsCoding(wbcRequest.getCode().getCoding(), null,
                null, "WBC"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, wbcRequest.getStatus());
        assertEquals(order.getDateActivated(), wbcRequest.getAuthoredOn());

        ProcedureRequest hbRequest = (ProcedureRequest) getFhirResourceById(orderIdHb, mappedResources).getResource();
        assertLabOrder(hbRequest, orderIdHb);
        assertProvenance(mappedResources, hbRequest);
        assertTrue(containsCoding(hbRequest.getCode().getCoding(), null,
                null, "Hb Electrophoresis"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, hbRequest.getStatus());
        assertEquals(order.getDateActivated(), hbRequest.getAuthoredOn());

    }

    @Test
    public void shouldNotMapAStoppedTestOrder() throws Exception {
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(24);
        List<FHIRResource> fhirResources = testOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertTrue(fhirResources.isEmpty());
    }

    @Test
    public void shouldMapADiscontinuedOrder() throws Exception {
        FHIREncounter fhirEncounter = createFhirEncounter();
        Order order = orderService.getOrder(25);

        List<FHIRResource> fhirResources = testOrderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertEquals(2, fhirResources.size());

        ProcedureRequest procedureRequest = (ProcedureRequest) fhirResources.get(0).getResource();
        assertLabOrder(procedureRequest, order.getPreviousOrder().getUuid() + "#1");
        assertProvenance(fhirResources, procedureRequest);
        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), null, null, "Hb Electrophoresis"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.CANCELLED, procedureRequest.getStatus());
        assertEquals(order.getPreviousOrder().getDateActivated(), procedureRequest.getAuthoredOn());

        Reference historyReference = procedureRequest.getRelevantHistory().get(0);
        assertEquals("urn:uuid:" + order.getPreviousOrder().getUuid() + "-provenance", historyReference.getReference());
    }

    private void assertLabOrder(ProcedureRequest procedureRequest, String orderId) {
        assertEquals(patientRef, procedureRequest.getSubject().getReference());
        assertTrue(procedureRequest.getRequester().getAgent().getReference().endsWith("812.json"));
        orderId = "urn:uuid:" + orderId;
        assertEquals(orderId, procedureRequest.getId());
        assertEquals(ProcedureRequest.ProcedureRequestIntent.ORDER, procedureRequest.getIntent());

        assertEquals(1, procedureRequest.getIdentifier().size());
        assertFalse(procedureRequest.getIdentifier().get(0).isEmpty());
        assertEquals(orderId, procedureRequest.getIdentifierFirstRep().getValue());

        Coding category = procedureRequest.getCategoryFirstRep().getCodingFirstRep();
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/order-type", category.getSystem());
        assertEquals(MRSProperties.TR_ORDER_TYPE_LAB_CODE, category.getCode());

        assertEquals(fhirEncounterId, procedureRequest.getContext().getReference());
        String fhirExtensionUrl = getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME);
        List<Extension> extensions = procedureRequest.getExtensionsByUrl(fhirExtensionUrl);
        assertEquals(0, extensions.size());
    }

    private FHIREncounter createFhirEncounter() {
        org.hl7.fhir.dstu3.model.Encounter encounter = new org.hl7.fhir.dstu3.model.Encounter();
        encounter.setSubject(new Reference(patientRef));
        encounter.setId(fhirEncounterId);
        return new FHIREncounter(encounter);
    }

    private void assertProvenance(List<FHIRResource> mappedResources, ProcedureRequest procedureRequest) {
        String id = procedureRequest.getId();
        Provenance provenance = (Provenance) getFhirResourceById(id + "-provenance", mappedResources).getResource();
        assertTrue(((Reference) provenance.getAgent().get(0).getWho()).getReference().endsWith("812.json"));
        assertEquals(provenance.getTargetFirstRep().getReference(), id);
    }

    private FHIRResource getFhirResourceById(String id, List<FHIRResource> mappedResources) {
        return mappedResources.stream().filter(fhirResource -> fhirResource.getResource().getId().endsWith(id)).findFirst().orElse(null);
    }


}
