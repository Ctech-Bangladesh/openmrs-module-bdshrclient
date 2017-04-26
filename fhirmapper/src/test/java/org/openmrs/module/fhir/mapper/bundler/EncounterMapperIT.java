package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.TemporalPrecisionEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EncounterMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    EncounterService encounterService;

    @Autowired
    EncounterMapper encounterMapper;

    @Autowired
    VisitService visitService;

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
        assertEquals("http://localhost:9997/api/1.0/facilities/1300012.json", fhirEncounter.getServiceProvider().getReference());
    }

    @Test
    public void shouldTakeConfiguredFacilityIdAsServiceProviderIdWhenLocationIsNotTaggedAsADGHSFacility() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(37);

        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));
        assertEquals("http://localhost:9997/api/1.0/facilities/1.json", fhirEncounter.getServiceProvider().getReference());
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
        assertEquals("http://localhost:9997/api/1.0/providers/23.json", fhirEncounter.getFirstParticipantReference().getReference());
    }

    @Test
    public void shouldSetPeriodFromVisitStartAndEndDate() throws Exception {
        Encounter savedEncounter = encounterService.getEncounter(36);
        Visit encounterVisit = visitService.getVisit(1);

        FHIREncounter fhirEncounter = encounterMapper.map(savedEncounter, "1234", getSystemProperties("1"));

        Period expectedPeriod = new Period();
        expectedPeriod.setStart(encounterVisit.getStartDatetime(), TemporalPrecisionEnum.SECOND);
        expectedPeriod.setEnd(encounterVisit.getStopDatetime(), TemporalPrecisionEnum.SECOND);

        assertEquals(expectedPeriod.getStart(), fhirEncounter.getEncounter().getPeriod().getStart());
        assertEquals(expectedPeriod.getEnd(), fhirEncounter.getEncounter().getPeriod().getEnd());
    }
}