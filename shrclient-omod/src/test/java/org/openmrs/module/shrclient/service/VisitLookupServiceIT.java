package org.openmrs.module.shrclient.service;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;

import static org.junit.Assert.*;
@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class VisitLookupServiceIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private VisitLookupService visitLookupService;
    @Autowired
    private VisitService visitService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private PatientService patientService;

    @Test
    public void shouldReturnCorrectExistingVisit() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date visitDateOne = DateUtil.parseDate("2014-07-10 13:00:00");
        Visit existingVisitOne = visitService.getVisit(100);

        Visit createdVisitOne = visitLookupService.findOrInitializeVisit(patient, visitDateOne, visitType, location, null ,null );
        assertEquals(existingVisitOne.getUuid(), createdVisitOne.getUuid());

        Date startdate = DateUtil.parseDate("2015-07-10 05:00:00");
        Visit existingVisitTwo = visitService.getVisit(102);

        Visit createdVisitTwo = visitLookupService.findOrInitializeVisit(patient, startdate, visitType, location, startdate ,null );
        assertEquals(existingVisitTwo.getUuid(), createdVisitTwo.getUuid());

        startdate = DateUtil.parseDate("2015-07-10 07:00:00");
        Visit existingVisitThree = visitService.getVisit(103);

        Visit createdVisitThree = visitLookupService.findOrInitializeVisit(patient, startdate, visitType, location, startdate ,null );
        assertEquals(existingVisitThree.getUuid(), createdVisitThree.getUuid());
    }

    @Test
    public void shouldReturnNewVisitIfVisitTypeIsDifferent() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(12);
        Location location = locationService.getLocation(13);
        Date visitDate = DateUtil.parseDate("2014-07-10 13:00:00");
        Visit existingVisit = visitService.getVisit(100);

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location,null ,null);

        assertNotEquals(existingVisit, createdVisit);
        assertEquals(visitDate, createdVisit.getStartDatetime());
        Date expectedStopDate = DateUtil.parseDate("2014-07-10 23:59:59");
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldReturnNewVisitIfLocationIsDifferent() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(14);
        Date visitDate = DateUtil.parseDate("2014-07-10 13:00:00");
        Visit existingVisit = visitService.getVisit(100);

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location, null ,null );

        assertNotEquals(existingVisit, createdVisit);
        assertEquals(visitDate, createdVisit.getStartDatetime());
        Date expectedStopDate = DateUtil.parseDate("2014-07-10 23:59:59");
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldReturnNewVisitIfPatientIsDifferent() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(101);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Visit existingVisit = visitService.getVisit(100);
        Date visitDate = DateUtil.parseDate("2014-07-10 13:00:00");

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location, null ,null );

        assertNotEquals(existingVisit, createdVisit);
        assertEquals(visitDate, createdVisit.getStartDatetime());
        Date expectedStopDate = DateUtil.parseDate("2014-07-10 23:59:59");
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldReturnNewVisitIfVisitDateIsBeforeExistingVisitStartDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date visitDate = DateUtil.parseDate("2014-07-10 01:00:00");
        Visit existingVisit = visitService.getVisit(100);

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location, null ,null );
        assertNotEquals(existingVisit, createdVisit);
        assertEquals(visitDate, createdVisit.getStartDatetime());
        Date expectedStopDate = DateUtil.parseDate("2014-07-10 04:59:59");
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldReturnNewVisitIfVisitDateIsAfterExistingVisitStopDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date visitDate = DateUtil.parseDate("2014-07-10 23:00:00");
        Visit existingVisit = visitService.getVisit(100);
        
        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location, null ,null );
        
        assertNotEquals(existingVisit, createdVisit);
        assertEquals(visitDate, createdVisit.getStartDatetime());
        Date expectedStopDate = DateUtil.parseDate("2014-07-10 23:59:59");
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldStopANewVisitAtEndOfDay() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date visitDate = new Date();

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location, null ,null );
        Date expectedStopDate = new DateTime(visitDate).withTime(23, 59, 59, 0).toDate();
        assertEquals(visitDate, createdVisit.getStartDatetime());
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldStopADownloadedVisitImmediatelyIfOnTheSameDay() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date visitDate = DateUtil.parseDate("2014-08-10 23:00:00");
        Date currentDateTime = DateUtil.parseDate("2014-08-10 23:30:00");

        org.joda.time.DateTimeUtils.setCurrentMillisFixed(currentDateTime.getTime());

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, visitDate, visitType, location,  null ,null );
        Date expectedStopDate = new DateTime(visitDate).withTime(23, 29, 59, 0).toDate();
        assertEquals(visitDate, createdVisit.getStartDatetime());
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldIncreaseStopTimeForApplicableVisitIfEncounterDateIsAfterCurrentStopDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(101);
        VisitType visitType = visitService.getVisitType(12);
        Location location = locationService.getLocation(14);
        Date encounterDate = DateUtil.parseDate("2014-07-11 06:00:00");
        Date startDate = DateUtil.parseDate("2014-07-11 05:00:00");

        Date currentDateTime = DateUtil.parseDate("2014-07-11 23:30:00");
        Visit existingVisit = visitService.getVisit(101);
        Date existingVisitStopDatetime = existingVisit.getStopDatetime();
        org.joda.time.DateTimeUtils.setCurrentMillisFixed(currentDateTime.getTime());

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, encounterDate, visitType, location, startDate , null);

        assertEquals(existingVisit.getUuid(), createdVisit.getUuid());
        assertEquals(startDate, createdVisit.getStartDatetime());
        assertEquals(existingVisitStopDatetime, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldIncreaseStopTimeForApplicableVisitToNextDayIfEncounterDateIsAfterCurrentVisitStopDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(101);
        VisitType visitType = visitService.getVisitType(12);
        Location location = locationService.getLocation(14);
        Date encounterDate = DateUtil.parseDate("2014-07-12 06:00:00");
        Date startDate = DateUtil.parseDate("2014-07-11 05:00:00");

        Date currentDateTime = DateUtil.parseDate("2014-07-13 23:30:00");
        Visit existingVisit = visitService.getVisit(101);
        org.joda.time.DateTimeUtils.setCurrentMillisFixed(currentDateTime.getTime());

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, encounterDate, visitType, location, startDate , null);

        assertEquals(existingVisit.getUuid(), createdVisit.getUuid());
        assertEquals(startDate, createdVisit.getStartDatetime());
        Date expectedStopDate = new DateTime(encounterDate).withTime(23, 59, 59, 0).toDate();
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldNotChangeStopDateIfVisitStopDateIsAfterEncounterDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(100);
        VisitType visitType = visitService.getVisitType(11);
        Location location = locationService.getLocation(13);
        Date encounterDate = DateUtil.parseDate("2015-08-10 12:00:00");
        Date startDate = DateUtil.parseDate("2015-08-10 07:00:00");

        Date currentDateTime = DateUtil.parseDate("2015-08-16 07:00:00");
        Visit existingVisit = visitService.getVisit(104);
        Date existingVisitStartDatetime = existingVisit.getStartDatetime();
        Date existingVisitStopDatetime = existingVisit.getStopDatetime();
        org.joda.time.DateTimeUtils.setCurrentMillisFixed(currentDateTime.getTime());

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, encounterDate, visitType, location, startDate , null);

        assertEquals(existingVisit.getUuid(), createdVisit.getUuid());
        assertEquals(existingVisitStartDatetime, createdVisit.getStartDatetime());
        assertEquals(existingVisitStopDatetime, createdVisit.getStopDatetime());
    }

    @Test
    public void shouldStopAvisitBasedOnStopDate() throws Exception {
        executeDataSet("testDataSets/visitDs.xml");
        Patient patient = patientService.getPatient(101);
        VisitType visitType = visitService.getVisitType(12);
        Location location = locationService.getLocation(14);
        Date encounterDate = DateUtil.parseDate("2014-07-13 16:00:00");
        Date startDate = DateUtil.parseDate("2014-07-13 05:00:00");
        Date stopDate = DateUtil.parseDate("2014-07-13 20:30:00");
        Date currentDateTime = DateUtil.parseDate("2014-07-13 23:30:00");
        org.joda.time.DateTimeUtils.setCurrentMillisFixed(currentDateTime.getTime());

        Visit createdVisit = visitLookupService.findOrInitializeVisit(patient, encounterDate, visitType, location, startDate , stopDate);

        assertEquals(startDate, createdVisit.getStartDatetime());
        Date expectedStopDate = new DateTime(encounterDate).withTime(20, 30, 00, 0).toDate();
        assertEquals(expectedStopDate, createdVisit.getStopDatetime());
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}