package org.openmrs.module.shrclient.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.assertEquals;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EMRPatientDeathServiceIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private PatientService patientService;
    @Autowired
    private EMRPatientDeathService patientDeathService;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/omrsGlobalPropertyTestDS.xml");
    }

    @Test
    public void shouldGetCauseOfDeathOfPatientIfAnyObservationCapturedCauseOfDeath() throws Exception {
        executeDataSet("testDataSets/patientDeathNoteDS.xml");
        Patient patient = patientService.getPatient(1);

        Concept actualCauseOfDeath = patientDeathService.getCauseOfDeath(patient);
        assertEquals("HIV", actualCauseOfDeath.getName().getName());
    }

    @Test
    public void shouldReturnUnspecifiedCauseOfDeathIfThereIsNoObservationCapturedAndPatientIsToBeMarkedDead() throws Exception {
        executeDataSet("testDataSets/patientDeathNoteDS.xml");
        Patient patient = patientService.getPatient(4);
        patient.setDead(true);

        Concept actualCauseOfDeath = patientDeathService.getCauseOfDeath(patient);
        assertEquals("Unspecified Cause Of Death", actualCauseOfDeath.getName().getName());
    }

    @Test
    public void shouldReturnUnspecifiedCauseOfDeathIfThePatientIsNewAndPatientIsToBeMarkedDead() throws Exception {
        executeDataSet("testDataSets/patientDeathNoteDS.xml");
        Patient patient = new Patient();
        patient.setDead(true);

        Concept actualCauseOfDeath = patientDeathService.getCauseOfDeath(patient);
        assertEquals("Unspecified Cause Of Death", actualCauseOfDeath.getName().getName());
    }

    @Test
    public void shouldReturnCauseOfDeathIfCauseOfDeathAttributeIsOtherThanUnspecifiedCauseOfDeath() throws Exception {
        executeDataSet("testDataSets/patientDeathNoteDS.xml");
        Patient patient = patientService.getPatient(3);

        Concept actualCauseOfDeath = patientDeathService.getCauseOfDeath(patient);
        assertEquals("CANCER", actualCauseOfDeath.getName().getName());
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

}