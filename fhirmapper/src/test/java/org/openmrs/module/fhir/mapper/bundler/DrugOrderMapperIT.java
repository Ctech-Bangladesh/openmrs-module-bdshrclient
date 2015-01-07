package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.instance.model.*;
import org.hl7.fhir.instance.model.MedicationPrescription.MedicationPrescriptionDosageInstructionComponent;
import org.hl7.fhir.instance.model.Schedule.ScheduleRepeatComponent;
import org.hl7.fhir.instance.model.Schedule.UnitsOfTime;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.hl7.fhir.instance.model.Schedule.UnitsOfTime.d;
import static org.hl7.fhir.instance.model.Schedule.UnitsOfTime.wk;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.TestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
public class DrugOrderMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private DrugOrderMapper orderMapper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
    }

    @Test
    public void shouldMapDrugOrders() throws Exception {
        Order order = orderService.getOrder(16);
        Encounter fhirEncounter = getFhirEncounter();

        assertTrue(orderMapper.canHandle(order));
        List<FHIRResource> FHIRResources = orderMapper.map(order, fhirEncounter, new AtomFeed(), getSystemProperties("1"));

        assertEquals(1, FHIRResources.size());
        MedicationPrescription prescription = (MedicationPrescription) FHIRResources.get(0).getResource();
        assertMedicationPrescription(prescription);
        assertEquals(1, prescription.getDosageInstruction().size());
        MedicationPrescriptionDosageInstructionComponent dosageInstruction = prescription.getDosageInstruction().get(0);
        assertRoute(dosageInstruction);
        assertDoseQuantity(dosageInstruction);
        assertSchedule(dosageInstruction, 6, 1, 1, d);
    }

    @Test
    public void shouldCalculateSchedulesForTwiceAWeek() throws Exception {
        Order order = orderService.getOrder(17);
        Encounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> FHIRResources = orderMapper.map(order, fhirEncounter, new AtomFeed(), getSystemProperties("1"));
        MedicationPrescription prescription = (MedicationPrescription) FHIRResources.get(0).getResource();
        assertSchedule(prescription.getDosageInstruction().get(0), 10, 1, 2, wk);
    }

    @Test
    public void shouldCalculateSchedulesForEveryThreeHoursFor10Weeks() throws Exception {
        Order order = orderService.getOrder(18);
        Encounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> FHIRResources = orderMapper.map(order, fhirEncounter, new AtomFeed(), getSystemProperties("1"));
        MedicationPrescription prescription = (MedicationPrescription) FHIRResources.get(0).getResource();
        assertSchedule(prescription.getDosageInstruction().get(0), 70, 1, 8, d);
    }

    @Test
    public void shouldCalculateSchedulesForEveryTwoHoursFor48Hours() throws Exception {
        Order order = orderService.getOrder(19);
        Encounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> FHIRResources = orderMapper.map(order, fhirEncounter, new AtomFeed(), getSystemProperties("1"));
        MedicationPrescription prescription = (MedicationPrescription) FHIRResources.get(0).getResource();
        assertSchedule(prescription.getDosageInstruction().get(0), 2, 1, 12, d);
    }

    private Encounter getFhirEncounter() {
        Encounter fhirEncounter = new Encounter();
        fhirEncounter.setSubject(new ResourceReference().setReferenceSimple("hid"));
        Encounter.EncounterParticipantComponent encounterParticipantComponent = fhirEncounter.addParticipant();
        encounterParticipantComponent.setIndividual(new ResourceReference().setReferenceSimple("provider"));
        return fhirEncounter;
    }

    private void assertSchedule(MedicationPrescriptionDosageInstructionComponent dosageInstruction, int expectedCount, int expectedDuration, int expectedFrequency, UnitsOfTime expectedUnits) throws ParseException {
        Schedule timing = (Schedule) dosageInstruction.getTiming();
        assertNotNull(timing);
        List<Period> event = timing.getEvent();
        assertEquals(1, event.size());
        assertEquals((new SimpleDateFormat("yyyy-MM-dd")).parse("2008-08-09"), DateUtil.parseDate(event.get(0).getStartSimple().toString()));
        ScheduleRepeatComponent repeat = timing.getRepeat();
        assertNotNull(repeat);
        assertEquals(expectedCount, repeat.getCountSimple());
        assertEquals(expectedDuration, repeat.getDurationSimple().intValue());
        assertEquals(expectedFrequency, repeat.getFrequencySimple());
        assertEquals(expectedUnits, repeat.getUnitsSimple());
    }

    private void assertDoseQuantity(MedicationPrescriptionDosageInstructionComponent dosageInstruction) {
        Quantity doseQuantity = dosageInstruction.getDoseQuantity();
        assertNotNull(doseQuantity);
        assertEquals("http://tr/openmrs/ws/rest/v1/tr/vs/sample-units", doseQuantity.getSystemSimple());
        assertEquals("mg", doseQuantity.getCodeSimple());
        assertTrue(4 == doseQuantity.getValueSimple().doubleValue());
    }

    private void assertRoute(MedicationPrescriptionDosageInstructionComponent dosageInstruction) {
        Coding routeCode = dosageInstruction.getRoute().getCoding().get(0);
        assertEquals("Oral", routeCode.getDisplaySimple());
        assertEquals("Oral", routeCode.getCodeSimple());
        assertEquals("http://tr/openmrs/ws/rest/v1/tr/vs/sample-route", routeCode.getSystemSimple());
    }

    private void assertMedicationPrescription(MedicationPrescription prescription) {
        assertNotNull(prescription);
        assertEquals("hid", prescription.getPatient().getReferenceSimple());
        assertNotNull(prescription.getIdentifier());
        assertEquals("2008-08-19T12:20:22+05:30", prescription.getDateWritten().getValue().toString());
        assertEquals("drugs/104", prescription.getMedication().getReferenceSimple());
        assertEquals("Lactic Acid", prescription.getMedication().getDisplaySimple());
        assertEquals("provider", prescription.getPrescriber().getReferenceSimple());
    }
}