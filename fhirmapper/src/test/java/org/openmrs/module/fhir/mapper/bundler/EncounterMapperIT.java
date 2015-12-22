package org.openmrs.module.fhir.mapper.bundler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EncounterMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    EncounterService encounterService;

    @Autowired
    EncounterMapper encounterMapper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/encounterMapperTestDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldTakeTheLocationIdAsServiceProviderIdWhenLocationIsTaggedAsADGHSFacility() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(36);

        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertEquals("http://localhost:9997/api/1.0/facilities/1300012.json", fhirEncounter.getServiceProvider().getReference().getValue());
    }

    @Test
    public void shouldTakeConfiguredFacilityIdAsServiceProviderIdWhenLocationIsNotTaggedAsADGHSFacility() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(37);

        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertEquals("http://localhost:9997/api/1.0/facilities/1.json", fhirEncounter.getServiceProvider().getReference().getValue());
    }

    @Test
    public void shouldNotSetParticipantIfEncounterHasNoProvider() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(37);
        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertNull(fhirEncounter.getFirstParticipantReference());
    }

    @Test
    public void shouldNotSetParticipantIfEncounterHasNoHIEProvider() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(36);
        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertNull(fhirEncounter.getFirstParticipantReference());
    }

    @Test
    public void shouldSetParticipantIfEncounterHasHIEProvider() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(38);
        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertEquals(1, fhirEncounter.getParticipantReferences().size());
        assertEquals("http://localhost:9997/api/1.0/providers/23.json", fhirEncounter.getFirstParticipantReference().getReference().getValue());
    }
}