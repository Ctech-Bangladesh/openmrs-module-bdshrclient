package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.FHIRFeedHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIREncounterMapperIntegrationTest extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIREncounterMapper fhirEncounterMapper;

    @Autowired
    private PatientService patientService;

    @Autowired
    ConceptService conceptService;

    public Bundle loadSampleFHIREncounter() throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/testFHIREncounter.xml", springContext);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapFhirEncounter() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        final Bundle encounterBundle = loadSampleFHIREncounter();
        assertEquals("Bundle/4fe6f9e2-d10a-4956-aae5-091e810090e1", encounterBundle.getId().getValue());

        FHIRFeedHelper.getComposition(encounterBundle);
        final Composition composition = FHIRFeedHelper.getComposition(encounterBundle);
        assertNotNull(composition);

        assertEquals(DateUtil.parseDate("2014-07-10T16:05:09+05:30"), composition.getDate());
        final Encounter encounter = FHIRFeedHelper.getEncounter(encounterBundle);
        assertNotNull(encounter);
        assertEquals("4d2f9872-4df1-438e-9d72-0a8b161d409b", encounter.getId().getIdPart());

        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        org.openmrs.Encounter emrEncounter = fhirEncounterMapper.map("HIDA764177", "shr_enc_id", emrPatient, encounterBundle, getSystemProperties("1"));

        assertNotNull(emrEncounter);
        assertEquals(emrPatient, emrEncounter.getPatient());
        assertEquals(DateUtil.parseDate("2014-07-10T16:05:09+05:30"), emrEncounter.getEncounterDatetime());
        assertEquals(encounter.getType().get(0).getText(), emrEncounter.getEncounterType().getName());
        assertNotNull(emrEncounter.getEncounterProviders());
        assertEquals("Bahmni", emrEncounter.getLocation().getName());

        assertNotNull(emrEncounter.getVisit());
        assertEquals("ad41fb41-a41a-4ad6-8835-2f59099acf5a", emrEncounter.getVisit().getUuid());
        assertEquals("50ab30be-98af-4dfd-bd04-5455937c443f", emrEncounter.getLocation().getUuid());
    }

    @Test
    public void shouldMapFhirConditions() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        final Bundle encounterBundle = loadSampleFHIREncounter();

        List<IResource> conditions = TestFhirFeedHelper.getResourceByType(encounterBundle, new Condition().getResourceName());
        assertEquals(2, conditions.size());
        assertEquals("http://mci.com//api/default/patients/HIDA764177", ((Condition)conditions.get(0)).getPatient().getReference().getValue());

        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        final org.openmrs.Encounter emrEncounter = fhirEncounterMapper.map("HIDA764177", "shr_enc_id", emrPatient, encounterBundle, getSystemProperties("1"));

        final Set<Obs> visitObs = emrEncounter.getObsAtTopLevel(false);
        assertEquals(2, visitObs.size());
        Obs firstObs = visitObs.iterator().next();
        assertNotNull(firstObs.getGroupMembers());
        assertNotNull(firstObs.getPerson());
        assertNotNull(firstObs.getEncounter());
    }

    @Test
    public void shouldMapAnEncounterWhichDoesNotHaveServiceProvider() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithoutServiceProvider.xml", springContext);
        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        org.openmrs.Encounter emrEncounter = fhirEncounterMapper.map("HIDA764177", "shr_enc_id", emrPatient, encounterBundle, getSystemProperties("1"));

        assertNotNull(emrEncounter);
        assertTrue(3 == emrEncounter.getLocation().getId());
    }

    @Test
    public void shouldSetShrClientSystemAsProviderIfNoParticipantsArePresent() throws Exception {
        executeDataSet("testDataSets/shrClientEncounterReverseSyncTestDS.xml");
        Bundle encounterBundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithoutParticipants.xml", springContext);
        org.openmrs.Patient emrPatient = patientService.getPatient(1);
        int shrClientSystemProviderId = 22;
        org.openmrs.Encounter emrEncounter = fhirEncounterMapper.map("HIDA764177", "shr_enc_id", emrPatient, encounterBundle, getSystemProperties("1"));

        assertNotNull(emrEncounter);
        assertEquals(1, emrEncounter.getEncounterProviders().size());
        assertThat(emrEncounter.getEncounterProviders().iterator().next().getProvider().getId(), is(shrClientSystemProviderId));
    }
}
