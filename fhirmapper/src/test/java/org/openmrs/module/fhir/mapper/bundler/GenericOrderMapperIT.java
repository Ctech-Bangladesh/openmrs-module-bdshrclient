package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.FHIRProperties.DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.getFhirExtensionUrl;
import static org.openmrs.module.fhir.MRSProperties.TR_ORDER_TYPE_RADIOLOGY_CODE;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;
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

        ProcedureRequest procedureRequest = (ProcedureRequest) mappedResources.get(0).getResource();

        assertProcedureRequest(procedureRequest, order.getUuid(), TR_ORDER_TYPE_RADIOLOGY_CODE);
        assertProvenance(mappedResources, procedureRequest);

        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), null, null, "X-ray left hand"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.ACTIVE, procedureRequest.getStatus());
        assertEquals(order.getDateActivated(), procedureRequest.getAuthoredOn());
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
        assertEquals(2, mappedResources.size());
        ProcedureRequest diagnosticOrder = (ProcedureRequest) TestFhirFeedHelper.getFirstResourceByType(new ProcedureRequest().getResourceType().name(), mappedResources).getResource();
        assertNotNull(diagnosticOrder);
        assertTrue(containsCoding(diagnosticOrder.getCode().getCoding(), "501qb827-a67c-4q1f-a705-e5efe0q6a972", "http://localhost:9997/openmrs/ws/rest/v1/tr/concept/501qb827-a67c-4q1f-a705-e5efe0q6a972", "X-Ray Right Chest"));
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
        assertEquals(2, fhirResources.size());
        ProcedureRequest procedureRequest = (ProcedureRequest) fhirResources.get(0).getResource();
        assertProcedureRequest(procedureRequest, order.getPreviousOrder().getUuid(), TR_ORDER_TYPE_RADIOLOGY_CODE);
        assertProvenance(fhirResources, procedureRequest);
        assertTrue(containsCoding(procedureRequest.getCode().getCoding(), "501qb827-a67c-4q1f-a705-e5efe0q6a972",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concept/501qb827-a67c-4q1f-a705-e5efe0q6a972", "X-Ray Right Chest"));
        assertEquals(ProcedureRequest.ProcedureRequestStatus.CANCELLED, procedureRequest.getStatus());
        assertEquals(order.getPreviousOrder().getDateActivated(), procedureRequest.getAuthoredOn());

        Reference historyReference = procedureRequest.getRelevantHistory().get(0);
        assertEquals("urn:uuid:" + order.getPreviousOrder().getUuid() + "-provenance", historyReference.getReference());
    }

    private void assertProcedureRequest(ProcedureRequest procedureRequest, String orderId, String orderTypeLabCode) {
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
        assertEquals(orderTypeLabCode, category.getCode());

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
        assertEquals(provenance.getRecorded(), procedureRequest.getAuthoredOn());
    }

    private FHIRResource getFhirResourceById(String id, List<FHIRResource> mappedResources) {
        return mappedResources.stream().filter(fhirResource -> fhirResource.getResource().getId().endsWith(id)).findFirst().orElse(null);
    }
}