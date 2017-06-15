package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.Date;
import java.util.Set;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.NEW;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.identifyFirstResourceWithName;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRProcedureRequestToLabOrderMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIRProcedureRequestMapper procedureRequestToTestOrderMapper;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private IdMappingRepository idMappingRepository;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleLabProcedureRequest() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithLabProcedureRequest.xml", applicationContext);
        Resource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceType().name());
        assertTrue(procedureRequestToTestOrderMapper.canHandle(resource));
    }

    @Test
    public void shouldHandleRadiologyProcedureRequest() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithRadiologyOrder.xml", applicationContext);
        Resource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceType().name());
        assertTrue(procedureRequestToTestOrderMapper.canHandle(resource));
    }

    @Test
    public void shouldNotHandleProcedureOrderProcedureRequest() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithExistingProcedureRequestForProcedureOrder.xml", applicationContext);
        Resource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceType().name());
        assertFalse(procedureRequestToTestOrderMapper.canHandle(resource));
    }

    @Test
    public void shouldMapALabProcedureRequest() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithLabProcedureRequest.xml", "HIDA764177", "shr-enc-id");
        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("7f7379ba-3ca8-11e3-bf2b-0800271c1b75", order.getConcept().getUuid());
        assertEquals(providerService.getProvider(23), order.getOrderer());
        assertEquals(orderService.getOrderType(16), order.getOrderType());
        assertEquals(orderService.getCareSetting(1), order.getCareSetting());
        assertEquals(DateUtil.parseDate("2015-08-24T17:10:10.000+05:30"), order.getDateActivated());
        assertEquals(DateUtil.parseDate("2015-08-25T17:10:10.000+05:30"), order.getAutoExpireDate());
            IdMapping idMapping = idMappingRepository.findByExternalId("shr-enc-id:453b7b24-7847-49f7-8a33-2fc339e5c4c7#TR:124", IdMappingType.PROCEDURE_REQUEST);
        assertEquals(order.getUuid(), idMapping.getInternalId());
        assertEquals(IdMappingType.PROCEDURE_REQUEST, idMapping.getType());
        assertEquals("http://shr.com/patients/HIDA764177/encounters/shr-enc-id#ProcedureRequest/453b7b24-7847-49f7-8a33-2fc339e5c4c7#TR:124",
                idMapping.getUri());
    }

    @Test
    public void shouldMapALabProcedureRequestWithoutRequester() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        int shrClientSystemProviderId = 22;
        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithLabProcedureRequestWithoutRequester.xml", "HIDA764177", "shr-enc-id-1");
        Set<Order> orders = emrEncounter.getOrders();
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertThat(order.getOrderer().getProviderId(), is(shrClientSystemProviderId));
    }

    @Test
    public void shouldUpdateSameEncounterIfNewOrdersAreAdded() throws Exception {
        String existingOrderId = "urn:uuid:453b7b24-7847-49f7-8a33-2fc339e5c4c7#1";
        String newOrderId = "urn:uuid:453b7b24-7847-49f7-8a33-2fc339e5c4c7#2";

        executeDataSet("testDataSets/labOrderDS.xml");
        Encounter existingEncounter = encounterService.getEncounter(42);
        Set<Order> orders = existingEncounter.getOrders();
        assertEquals(1, orders.size());
        Order existingOrder = orders.iterator().next();
        assertNull(existingOrder.getDateStopped());

        Bundle bundle = loadSampleFHIREncounter("encounterBundles/stu3/updateEncounterWithANewLabProcedureRequest.xml");
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "HIDA764177", "shr-enc-id-1");
        EmrEncounter emrEncounter = new EmrEncounter(existingEncounter);

        Resource resource = FHIRBundleHelper.findResourceByFirstReference(bundle, asList(new Reference().setReference(existingOrderId)));
        procedureRequestToTestOrderMapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));
        assertTrue(emrEncounter.getOrders().isEmpty());

        resource = FHIRBundleHelper.findResourceByFirstReference(bundle, asList(new Reference().setReference(newOrderId)));
        procedureRequestToTestOrderMapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));

        Set<Order> emrEncounterOrders = emrEncounter.getOrders();
        assertEquals(1, emrEncounterOrders.size());
        Order newOrder = emrEncounterOrders.iterator().next();
        assertEquals(NEW, newOrder.getAction());
        assertThat(newOrder.getConcept().getId(), is(303));
    }

    @Test
    public void shouldDiscontinueAnExistingOrderIfUpdatedAsCancelled() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        Encounter existingEncounter = encounterService.getEncounter(42);
        Set<Order> orders = existingEncounter.getOrders();
        assertEquals(1, orders.size());
        Order existingOrder = orders.iterator().next();
        assertNull(existingOrder.getDateStopped());

        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithCanceledLabProcedureRequest.xml", "HIDA764177", "shr-enc-id-1");
        Set<Order> emrEncounterOrders = emrEncounter.getOrders();
        assertEquals(1, emrEncounterOrders.size());
        Order discontinuedOrder = emrEncounterOrders.iterator().next();
        assertEquals(DISCONTINUE, discontinuedOrder.getAction());
        assertEquals(existingOrder, discontinuedOrder.getPreviousOrder());
        assertEquals(DateUtil.parseDate("2015-08-24T17:10:10+05:30"), discontinuedOrder.getDateActivated());
    }

    @Test
    public void shouldNotDoAnyThingIfOrderWasNotDownloadedAndUpdatedAsCancelled() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithCanceledLabProcedureRequest.xml", "HIDA764177", "shr-enc-id-2");
        Set<Order> emrEncounterOrders = emrEncounter.getOrders();
        assertTrue(CollectionUtils.isEmpty(emrEncounterOrders));
    }

    @Test
    public void shouldMapLabOrderFromCategory() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/diagnositicOrderWithEventDateAndCategory.xml", "HIDA764177", "shr-enc-id-1");
        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals(MRSProperties.MRS_LAB_ORDER_TYPE, order.getOrderType().getName());
    }

    @Test
    public void shouldSetOrderDateActivatedFromEncounterIfEventNotPresent() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        Encounter encounter = new Encounter();
        Date encounterDatetime = new Date();
        encounter.setEncounterDatetime(encounterDatetime);

        Bundle bundle = loadSampleFHIREncounter("encounterBundles/stu3/encounterWithLabProcedureRequestWithoutAuthoredOn.xml");
        Resource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceType().name());
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "HIDA764177", "shr-enc-id-1");
        EmrEncounter emrEncounter = new EmrEncounter(encounter);

        procedureRequestToTestOrderMapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));

        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("7f7379ba-3ca8-11e3-bf2b-0800271c1b75", order.getConcept().getUuid());
        assertEquals(encounterDatetime, order.getDateActivated());
        assertNotNull(order.getAutoExpireDate());
    }

    @Test
    public void shouldMapRadiologyOrders() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");

        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithRadiologyOrder.xml", "HIDA764177", "shr-enc-id");

        Set<Order> orders = emrEncounter.getOrders();
        assertFalse(orders.isEmpty());
        assertEquals(1, orders.size());
        Order order = orders.iterator().next();
        assertEquals("7f7379ba-3ca8-11e3-bf2b-0800271c1b75", order.getConcept().getUuid());
        assertEquals(providerService.getProvider(23), order.getOrderer());
        assertEquals(orderService.getOrderType(16), order.getOrderType());
        assertEquals(orderService.getCareSetting(1), order.getCareSetting());
        assertEquals(DateUtil.parseDate("2016-03-11T13:02:16.000+05:30"), order.getDateActivated());
        assertEquals(DateUtil.parseDate("2016-03-12T13:02:16.000+05:30"), order.getAutoExpireDate());
        IdMapping idMapping = idMappingRepository.findByExternalId("shr-enc-id:bdee83c1-f559-433f-8932-8711f6174676", IdMappingType.PROCEDURE_REQUEST);
        assertEquals(order.getUuid(), idMapping.getInternalId());
        assertEquals("http://shr.com/patients/HIDA764177/encounters/shr-enc-id#ProcedureRequest/bdee83c1-f559-433f-8932-8711f6174676",
                idMapping.getUri());
    }

    @Test
    public void shouldSkipDownloadOfOrderIfCategoryCodeNotPresentLocalMaps() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");
        EmrEncounter emrEncounter = mapOrder("encounterBundles/stu3/encounterWithOrderCategoryCodeNotPresentLocally.xml", "HIDA764177", "shr-enc-id");
        assertTrue(CollectionUtils.isEmpty(emrEncounter.getOrders()));
    }

    private EmrEncounter mapOrder(String filePath, String healthId, String shrEncounterId) throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());
        Bundle bundle = loadSampleFHIREncounter(filePath);
        Resource resource = identifyFirstResourceWithName(bundle, new ProcedureRequest().getResourceType().name());
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, healthId, shrEncounterId);
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        procedureRequestToTestOrderMapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));
        return emrEncounter;
    }

    private Bundle loadSampleFHIREncounter(String filePath) throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter(filePath, springContext);
    }
}