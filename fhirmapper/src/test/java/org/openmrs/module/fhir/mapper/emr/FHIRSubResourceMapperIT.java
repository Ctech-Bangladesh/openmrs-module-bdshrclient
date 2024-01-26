package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRSubResourceMapperIT extends BaseModuleWebContextSensitiveTest {
    private MapperTestHelper mapperTestHelper;

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private FHIRSubResourceMapper fhirSubResourceMapper;
    @Autowired
    private EncounterService encounterService;

    @Before
    public void setUp() throws Exception {
        mapperTestHelper = new MapperTestHelper();

    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldAddNewOrdersToEncounter() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");
        Encounter existingEncounter = encounterService.getEncounter(42);
        assertEquals(1, existingEncounter.getOrders().size());
        Bundle bundle = loadSampleFHIREncounter("encounterBundles/stu3/updateEncounterWithANewLabProcedureRequest.xml");
        fhirSubResourceMapper.map(existingEncounter, new ShrEncounterBundle(bundle, "HIDA764177", "SHR-ENC-1"), getSystemProperties("1"));
        assertEquals(2, existingEncounter.getOrders().size());
    }

    @Test
    public void shouldNotMapTopLevelObsIfNoneOfTheFHIRObservationHaveValue() throws Exception {
        executeDataSet("testDataSets/encounterWithObsHavingIgnoredLeafLevelChildrenTestDs.xml");
        Bundle observationBundle = (Bundle) mapperTestHelper.loadSampleFHIREncounter("encounterBundles/stu3/encounterWithObservationWithoutValues.xml", springContext);

        Encounter openmrsEncounter = new Encounter();
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(observationBundle, "98104750156", "shr-enc-id-1");

        fhirSubResourceMapper.map(openmrsEncounter, encounterComposition, getSystemProperties("1"));

        assertTrue(CollectionUtils.isEmpty(openmrsEncounter.getObsAtTopLevel(false)));
    }


    @Test
    public void shouldNotMapAnObservationWhichDoesNotChildOrValue() throws Exception {
        executeDataSet("testDataSets/encounterWithObsHavingIgnoredLeafLevelChildrenTestDs.xml");
        Bundle observationBundle = (Bundle) mapperTestHelper.loadSampleFHIREncounter("encounterBundles/stu3/encounterWithPulseObservationHavingNoValue.xml", springContext);
        Resource observationResource = FHIRBundleHelper.findResourceByReference(observationBundle, new Reference("urn:uuid:56b0a203-9215-40ea-a6a0-b1d625d100c0"));

        Encounter openmrsEncounter = new Encounter();
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(observationBundle, "98104750156", "shr-enc-id-1");

        fhirSubResourceMapper.map(openmrsEncounter, encounterComposition, getSystemProperties("1"));

        assertEquals(1, openmrsEncounter.getObsAtTopLevel(false).size());

        Obs vitalsObs = openmrsEncounter.getObsAtTopLevel(false).iterator().next();
        assertEquals("Vitals(Unverified Term)", vitalsObs.getConcept().getName().getName());
        assertEquals(1, vitalsObs.getGroupMembers().size());

        Obs bpObs = vitalsObs.getGroupMembers().iterator().next();
        assertEquals(1, bpObs.getGroupMembers().size());
        assertEquals("Blood Pressure", bpObs.getConcept().getName().getName());

        Obs diastolicObs = bpObs.getGroupMembers().iterator().next();
        assertEquals("Diastolic", diastolicObs.getConcept().getName().getName());
        assertTrue(CollectionUtils.isEmpty(diastolicObs.getGroupMembers()));
        assertNotNull(diastolicObs.getValueNumeric());
    }

    @Test
    public void shouldNotMapAnHierarchyWhenNoneOfTheChildrenHaveValue() throws Exception {
        executeDataSet("testDataSets/encounterWithObsHavingIgnoredLeafLevelChildrenTestDs.xml");
        Bundle observationBundle = (Bundle) mapperTestHelper.loadSampleFHIREncounter("encounterBundles/stu3/encounterWithBPObservationHierarchyHavingNoValue.xml", springContext);

        Encounter openmrsEncounter = new Encounter();
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(observationBundle, "98104750156", "shr-enc-id-1");

        fhirSubResourceMapper.map(openmrsEncounter, encounterComposition, getSystemProperties("1"));

        assertEquals(1, openmrsEncounter.getObsAtTopLevel(false).size());
        Obs vitalsObs = openmrsEncounter.getObsAtTopLevel(false).iterator().next();
        assertEquals("Vitals(Unverified Term)", vitalsObs.getConcept().getName().getName());
        assertEquals(1, vitalsObs.getGroupMembers().size());

        Obs pulseObs = vitalsObs.getGroupMembers().iterator().next();
        assertEquals("Pulse", pulseObs.getConcept().getName().getName());
        assertTrue(CollectionUtils.isEmpty(pulseObs.getGroupMembers()));
        assertNotNull(pulseObs.getValueNumeric());
    }


    private Bundle loadSampleFHIREncounter(String filePath) throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter(filePath, springContext);
    }
}
