package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.ProcedureRequest;
import ca.uhn.fhir.model.dstu2.valueset.ProcedureRequestStatusEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static ca.uhn.fhir.model.dstu2.valueset.ProcedureRequestStatusEnum.REQUESTED;
import static ca.uhn.fhir.model.dstu2.valueset.ProcedureRequestStatusEnum.SUSPENDED;
import static org.junit.Assert.*;
import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.NEW;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;
import static org.openmrs.module.fhir.utils.DateUtil.parseDate;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.identifyFirstResourceWithName;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.identifyResourcesByName;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRProcedureRequestMapperIT extends BaseModuleWebContextSensitiveTest {
    private final String PATIENT_HEALTH_ID = "HIDA764177";
    private final String SHR_ENC_ID = "shr-enc-id-1";
    @Autowired
    private FHIRProcedureRequestMapper procedureRequestMapper;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private IdMappingRepository idMappingRepository;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/procedureOrderDS.xml");
    }

    @Test
    public void shouldHandleAProcedureRequest() throws Exception {
        Bundle bundle = loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedureRequest.xml");
        IResource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceName());
        assertTrue(procedureRequestMapper.canHandle(resource));
    }

    @Test
    public void shouldMapAProcedureRequest() throws Exception {
        EmrEncounter emrEncounter = mapOrder("encounterBundles/dstu2/encounterWithProcedureRequest.xml", REQUESTED);

        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("Colposcopy", order.getConcept().getName().getName());
        assertEquals(NEW, order.getAction());
        assertEquals(providerService.getProvider(22), order.getOrderer());
        assertEquals(orderService.getOrderType(16), order.getOrderType());
        assertEquals(orderService.getCareSetting(1), order.getCareSetting());
        assertEquals(parseDate("2016-02-03T15:30:20+05:30"), order.getDateActivated());
        assertEquals(parseDate("2016-02-04T15:30:20+05:30"), order.getAutoExpireDate());
        assertEquals("Some Notes", order.getCommentToFulfiller());

        IdMapping mapping = idMappingRepository.findByInternalId(order.getUuid(), IdMappingType.PROCEDURE_ORDER);
        assertNotNull(mapping);
        assertEquals(SHR_ENC_ID + ":f3703dad-7e1e-47b6-9952-4ca5774fb365", mapping.getExternalId());
        String expected = String.format("http://shr.com/patients/%s/encounters/%s#ProcedureRequest/f3703dad-7e1e-47b6-9952-4ca5774fb365", PATIENT_HEALTH_ID, SHR_ENC_ID);
        assertEquals(expected, mapping.getUri());
    }

    @Test
    public void shouldNotMapAProcedureRequestWhenConceptIsNotFound() throws Exception {
        EmrEncounter emrEncounter = mapOrder("encounterBundles/dstu2/encounterWithProcedureRequestWithNotSyncedConcept.xml", REQUESTED);

        Set<Order> orders = emrEncounter.getOrders();
        assertTrue(orders.isEmpty());
    }

    @Test
    public void shouldNotMapARequestIfAlreadyPresent() throws Exception {
        Encounter existingEncounter = encounterService.getEncounter(38);
        Set<Order> existingOrders = existingEncounter.getOrders();
        assertEquals(1, existingOrders.size());
        Order existingOrder = existingOrders.iterator().next();
        assertEquals("Colposcopy", existingOrder.getConcept().getName().getName());
        assertEquals(NEW, existingOrder.getAction());

        EmrEncounter emrEncounter = mapOrder("encounterBundles/dstu2/encounterWithExistingProcedureRequest.xml", existingEncounter, REQUESTED);
        
        Set<Order> orders = emrEncounter.getOrders();
        assertTrue(orders.isEmpty());
    }

    @Test
    public void shouldDiscontinueAnExistingOrderIfUpdatedAsSuspended() throws Exception {
        Encounter existingEncounter = encounterService.getEncounter(38);
        Set<Order> existingOrders = existingEncounter.getOrders();
        assertEquals(1, existingOrders.size());
        Order existingOrder = existingOrders.iterator().next();
        assertEquals("Colposcopy", existingOrder.getConcept().getName().getName());
        assertEquals(NEW, existingOrder.getAction());

        EmrEncounter emrEncounter = mapOrder("encounterBundles/dstu2/encounterWithSuspendedProcedureRequest.xml", existingEncounter, SUSPENDED);

        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("Colposcopy", order.getConcept().getName().getName());
        assertEquals(DISCONTINUE, order.getAction());
        assertEquals(existingOrder, order.getPreviousOrder());
        assertEquals(providerService.getProvider(22), order.getOrderer());
        assertEquals(orderService.getOrderType(16), order.getOrderType());
        assertEquals(orderService.getCareSetting(1), order.getCareSetting());
        assertEquals(parseDate("2016-02-03T15:32:39+05:30"), order.getDateActivated());
        assertEquals(parseDate("2016-02-04T15:32:39+05:30"), order.getAutoExpireDate());
    }

    @Test
    public void shouldNotDownloadIfProcedureRequestWasNotDownloadedAndUpdatedAsSuspended() throws Exception {
        EmrEncounter emrEncounter = mapOrder("encounterBundles/dstu2/encounterWithSuspendedProcedureRequestWIthNotDownloadedOrder.xml", SUSPENDED);

        Set<Order> orders = emrEncounter.getOrders();
        assertTrue(orders.isEmpty());
    }

    private EmrEncounter mapOrder(String filePath, ProcedureRequestStatusEnum requestStatus) throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());
        return mapOrder(filePath, encounter, requestStatus);
    }

    private EmrEncounter mapOrder(String filePath, Encounter encounter, ProcedureRequestStatusEnum requestStatus) throws Exception {
        Bundle bundle = loadSampleFHIREncounter(filePath);
        ProcedureRequest procedureRequest = getProcedureRequestByStatus(bundle, requestStatus);
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, PATIENT_HEALTH_ID, SHR_ENC_ID);
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        procedureRequestMapper.map(procedureRequest, emrEncounter, encounterComposition, getSystemProperties("1"));
        return emrEncounter;
    }

    private ProcedureRequest getProcedureRequestByStatus(Bundle bundle, ProcedureRequestStatusEnum requestStatus) {
        List<IResource> resources = identifyResourcesByName(bundle, new ProcedureRequest().getResourceName());
        for (IResource resource : resources) {
            ProcedureRequest request = (ProcedureRequest) resource;
            if (requestStatus.getCode().equals(request.getStatus())) {
                return request;
            }
        }
        return null;
    }

    private Bundle loadSampleFHIREncounter(String filePath) throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter(filePath, applicationContext);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}