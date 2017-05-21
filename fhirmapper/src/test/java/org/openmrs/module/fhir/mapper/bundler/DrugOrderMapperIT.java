package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestIntent.ORDER;
import static org.hl7.fhir.dstu3.model.MedicationRequest.MedicationRequestStatus.ACTIVE;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DrugOrderMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private DrugOrderMapper orderMapper;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");
    }

    @Test
    public void shouldMapMedicationRequestDateAndRouteAndOrderer() throws Exception {
        Order order = orderService.getOrder(16);
        FHIREncounter fhirEncounter = getFhirEncounter();

        assertTrue(orderMapper.canHandle(order));
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));

        assertEquals(2, fhirResources.size());
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertMedicationRequest(medicationRequest, order.getDateActivated());
        assertProvenance(fhirResources, medicationRequest, order.getScheduledDate(), null);
        assertTimingRepeat(medicationRequest.getDosageInstructionFirstRep(), 1, 1, Timing.UnitsOfTime.D,
                6, Timing.UnitsOfTime.D);

    }

    @Test
    public void shouldNotSetRequesterIfNotHIEProvider() throws Exception {
        Order order = orderService.getOrder(17);
        FHIREncounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        assertEquals(2, fhirResources.size());
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertTrue(medicationRequest.getRequester().isEmpty());
    }

    @Test
    public void shouldCalculateSchedulesForTwiceAWeek() throws Exception {
        Order order = orderService.getOrder(17);
        FHIREncounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertTimingRepeat(medicationRequest.getDosageInstructionFirstRep(), 2, 1, Timing.UnitsOfTime.WK,
                10, Timing.UnitsOfTime.WK);
    }

    @Test
    public void shouldCalculateSchedulesForEveryThreeHoursFor10Weeks() throws Exception {
        Order order = orderService.getOrder(18);
        FHIREncounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertTimingRepeat(medicationRequest.getDosageInstructionFirstRep(), 1, 3, Timing.UnitsOfTime.H,
                10, Timing.UnitsOfTime.WK);
    }

    @Test
    public void shouldCalculateSchedulesForEveryTwoHoursFor48Hours() throws Exception {
        Order order = orderService.getOrder(19);
        FHIREncounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertMedicationRequest(medicationRequest, order.getDateActivated());
        assertTimingRepeat(medicationRequest.getDosageInstructionFirstRep(), 1, 2, Timing.UnitsOfTime.H,
                2, Timing.UnitsOfTime.D);
        assertProvenance(fhirResources, medicationRequest, order.getScheduledDate(), null);
    }

    @Test
    public void shouldSetScheduledDate() throws Exception {
        Order order = orderService.getOrder(20);
        FHIREncounter fhirEncounter = getFhirEncounter();

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertMedicationRequest(medicationRequest, order.getDateActivated());

        Provenance provenance = (Provenance) getFhirResourceById(medicationRequest.getId() + "-provenance", fhirResources).getResource();
        assertEquals(order.getScheduledDate(), provenance.getPeriod().getStart());
    }

    @Test
    public void shouldSetAsNeeded() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(20);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertTrue(medicationRequest.getDosageInstructionFirstRep().getAsNeededBooleanType().booleanValue());

        order = orderService.getOrder(19);
        fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertFalse(medicationRequest.getDosageInstructionFirstRep().getAsNeededBooleanType().booleanValue());
    }

    @Test
    public void shouldMapDoseFromMedicationFormsValueset() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();
        Order order = orderService.getOrder(19);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));

        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertDoseQuantity(medicationRequest.getDosageInstructionFirstRep(), "http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Medication-Forms", "Pill", "Pill");
    }

    @Test
    public void shouldMapDoseFromMedicationPackageFormsFormsValueset() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();
        Order order = orderService.getOrder(18);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));

        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertDoseQuantity(medicationRequest.getDosageInstructionFirstRep(), "http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Medication-Package-Forms", "Puffs", "Puffs");
    }

    @Test
    public void shouldNotSetSystemAndCodeIfDoseFromQuantityUnits() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();
        Order order = orderService.getOrder(17);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));

        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertDoseQuantity(medicationRequest.getDosageInstructionFirstRep(), null, null, "mg");
    }

    @Test
    public void shouldSetDispenseRequestForLocalQuantity() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(20);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        SimpleQuantity quantity = medicationRequest.getDispenseRequest().getQuantity();
        assertThat(quantity.getValue().doubleValue(), is(190.0));
        assertEquals("mg", quantity.getUnit());
    }

    @Test
    public void shouldMapAdditionalInstructionsAndNotes() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(21);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();
        assertTrue(containsCoding(medicationRequest.getDosageInstructionFirstRep().getAdditionalInstructionFirstRep().getCoding(),
                "1101", "/concepts/1101", "As directed"));
        assertEquals("additional instructions notes", medicationRequest.getNoteFirstRep().getText());
    }

    @Test
    public void shouldNotMapAdditionalInstructionsIfNull() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(29);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertTrue(medicationRequest.getDosageInstructionFirstRep().getAdditionalInstruction().isEmpty());
    }

    @Test
    public void shouldMapMorningAfternoonAndNightDose() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(22);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(1, medicationRequest.getDosageInstruction().size());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();
        List<Extension> extensions = dosageInstruction.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME));
        assertEquals(1, extensions.size());
        String value = ((StringType) extensions.get(0).getValue()).getValue();
        Map map = new ObjectMapper().readValue(value, Map.class);
        assertEquals(3, map.size());
        assertEquals(1.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY));
        assertEquals(2.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY));
        assertEquals(3.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY));
        assertEquals("TID", dosageInstruction.getTiming().getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void shouldMapMorningAfternoonDoseOnly() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(23);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(1, medicationRequest.getDosageInstruction().size());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();
        List<Extension> extensions = dosageInstruction.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME));
        assertEquals(1, extensions.size());
        String value = ((StringType) extensions.get(0).getValue()).getValue();
        Map map = new ObjectMapper().readValue(value, Map.class);
        assertEquals(2, map.size());
        assertEquals(11.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY));
        assertEquals(12.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY));
        assertNull(map.get(FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY));
        assertEquals("BID", dosageInstruction.getTiming().getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void shouldMapEveningDoseOnly() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(24);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(1, medicationRequest.getDosageInstruction().size());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();
        List<Extension> extensions = dosageInstruction.getExtensionsByUrl(FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME));
        assertEquals(1, extensions.size());
        String value = ((StringType) extensions.get(0).getValue()).getValue();
        Map map = new ObjectMapper().readValue(value, Map.class);
        assertEquals(1, map.size());
        assertNull(map.get(FHIRProperties.FHIR_DRUG_ORDER_MORNING_DOSE_KEY));
        assertNull(map.get(FHIRProperties.FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY));
        assertEquals(30.0, map.get(FHIRProperties.FHIR_DRUG_ORDER_EVENING_DOSE_KEY));
        assertEquals("QD", dosageInstruction.getTiming().getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void shouldSetStatusAndDateEndedForStoppedDrugOrders() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(25);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(MedicationRequest.MedicationRequestStatus.STOPPED, medicationRequest.getStatus());
        Provenance provenance = (Provenance) getFhirResourceById(medicationRequest.getId() + "-provenance", fhirResources).getResource();
        assertEquals(order.getDateStopped(), provenance.getPeriod().getEnd());
    }

    @Test
    public void shouldSetPreviousOrderReferenceForEditedDrugOrders() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(26);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(ACTIVE, medicationRequest.getStatus());
        assertEquals("urn:uuid:amkbja86-awaa-g1f3-9qw0-ccc2c6c63ab0", medicationRequest.getPriorPrescription().getReference());
    }

    @Test
    public void shouldSetPreviousOrderEncounterUrlForEditedDrugOrdersInDifferentEncounters() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(77);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(ACTIVE, medicationRequest.getStatus());
        assertEquals("encounters/shr_enc_id_1#MedicationRequest/amkbja86-awaa-g1f3-9qw0-ccc2c6c63ab0", medicationRequest.getPriorPrescription().getReference());
    }

    @Test
    public void shouldSetOrderActionExtension() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(24);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        Provenance provenance = (Provenance) getFhirResourceById(medicationRequest.getId() + "-provenance", fhirResources).getResource();
        assertEquals(order.getScheduledDate(), provenance.getPeriod().getStart());
        assertEquals(FHIR_DATA_OPERATION_VALUESET_URL, provenance.getActivity().getSystem());
        assertEquals(FHIR_DATA_OPERATION_CREATE_CODE, provenance.getActivity().getCode());

        order = orderService.getOrder(25);
        fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        provenance = (Provenance) getFhirResourceById(medicationRequest.getId() + "-provenance", fhirResources).getResource();
        assertEquals(order.getScheduledDate(), provenance.getPeriod().getStart());
        assertEquals(FHIR_DATA_OPERATION_VALUESET_URL, provenance.getActivity().getSystem());
        assertEquals(FHIR_DATA_OPERATION_ABORT_CODE, provenance.getActivity().getCode());

        order = orderService.getOrder(26);
        fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        provenance = (Provenance) getFhirResourceById(medicationRequest.getId() + "-provenance", fhirResources).getResource();
        assertEquals(order.getScheduledDate(), provenance.getPeriod().getStart());
        assertEquals(FHIR_DATA_OPERATION_VALUESET_URL, provenance.getActivity().getSystem());
        assertEquals(FHIR_DATA_OPERATION_UPDATE_CODE, provenance.getActivity().getCode());
    }

    @Test
    public void shouldMapNonCodedDrugOrders() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();

        Order order = orderService.getOrder(27);
        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertTrue(medicationRequest.getMedication() instanceof CodeableConcept);
        CodeableConcept medication = (CodeableConcept) medicationRequest.getMedication();
        assertEquals(1, medication.getCoding().size());
        Coding coding = medication.getCoding().get(0);
        assertEquals("Paracetamol 20mg", coding.getDisplay());
        assertNull(coding.getCode());
        assertNull(coding.getSystem());
    }

    @Test
    public void shouldMapADrugOrderWithoutDose() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();
        Order order = orderService.getOrder(28);

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(1, medicationRequest.getDosageInstruction().size());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();
        assertNull(dosageInstruction.getDose());
        assertTimingRepeat(dosageInstruction, 1, 2, Timing.UnitsOfTime.H,
                2, Timing.UnitsOfTime.D);
    }

    @Test
    public void shouldMapADrugOrderWithCustomDoseAsZero() throws Exception {
        FHIREncounter fhirEncounter = getFhirEncounter();
        Order order = orderService.getOrder(29);

        List<FHIRResource> fhirResources = orderMapper.map(order, fhirEncounter, new Bundle(), getSystemProperties("1"));
        MedicationRequest medicationRequest = (MedicationRequest) fhirResources.get(0).getResource();

        assertEquals(1, medicationRequest.getDosageInstruction().size());
        Dosage dosageInstruction = medicationRequest.getDosageInstructionFirstRep();

        SimpleQuantity dose = dosageInstruction.getDoseSimpleQuantity();
        assertNotNull(dose.getUnit());
        assertEquals(0, dosageInstruction.getTiming().getRepeat().getFrequency());
        assertNull(dosageInstruction.getTiming().getRepeat().getPeriod());
        assertNull(dosageInstruction.getTiming().getRepeat().getPeriodUnit());
        assertTrue(dosageInstruction.getTiming().getCode().isEmpty());

        String fhirExtensionUrl = FHIRProperties.getFhirExtensionUrl(FHIRProperties.DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME);
        assertTrue(CollectionUtils.isEmpty(dosageInstruction.getExtensionsByUrl(fhirExtensionUrl)));
    }

    private FHIREncounter getFhirEncounter() {
        Encounter encounter = new Encounter();
        encounter.setId("shrEncId");
        encounter.setSubject(new Reference().setReference("hid"));
        return new FHIREncounter(encounter);
    }

    private void assertTimingRepeat(Dosage dosageInstruction, int expectedFrequency,
                                    int expectedPeriod, Timing.UnitsOfTime expectedPeriodUnits, int expectedDuration,
                                    Timing.UnitsOfTime expectedDurationUnits) throws ParseException {
        Timing timing = dosageInstruction.getTiming();
        assertNotNull(timing);
        Timing.TimingRepeatComponent repeat = timing.getRepeat();
        assertNotNull(repeat);
        Duration bounds = (Duration) repeat.getBounds();
        assertEquals(expectedDuration, bounds.getValue().intValue());
        assertEquals(expectedDurationUnits.toCode(), bounds.getCode());
        assertNull(repeat.getDuration());
        assertTrue(expectedFrequency == repeat.getFrequency());
        assertEquals(expectedPeriod, repeat.getPeriod().intValue());
        assertEquals(expectedPeriodUnits, repeat.getPeriodUnit());
    }

    private void assertDoseQuantity(Dosage dosageInstruction, String valueSetUrl, String code, String displayUnit) throws FHIRException {
        assertTrue(dosageInstruction.getDose() instanceof SimpleQuantity);
        SimpleQuantity doseQuantity = dosageInstruction.getDoseSimpleQuantity();
        assertNotNull(doseQuantity);
        assertEquals(valueSetUrl, doseQuantity.getSystem());
        assertEquals(code, doseQuantity.getCode());
        assertEquals(displayUnit, doseQuantity.getUnit());
        assertTrue(4 == doseQuantity.getValue().doubleValue());
    }

    private void assertMedicationRequest(MedicationRequest medicationRequest, Date expectedDate) {
        assertNotNull(medicationRequest);
        assertEquals("hid", medicationRequest.getSubject().getReference());
        assertNotNull(medicationRequest.getIdentifier());
        assertEquals(ORDER, medicationRequest.getIntent());
        assertEquals("shrEncId", medicationRequest.getContext().getReference());
        assertEquals(1, medicationRequest.getDosageInstruction().size());

        assertEquals(expectedDate, medicationRequest.getAuthoredOn());
        List<Coding> coding = ((CodeableConcept) medicationRequest.getMedication()).getCoding();
        assertEquals(1, coding.size());
        assertTrue(containsCoding(coding, "104", "drugs/104", "Lactic Acid"));

        assertTrue(containsCoding(medicationRequest.getDosageInstructionFirstRep().getRoute().getCoding(),
                "Oral", "http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Route-of-Administration", "Oral"));

        assertTrue(medicationRequest.getRequester().getAgent().getReference().endsWith("321.json"));
    }

    private void assertProvenance(List<FHIRResource> mappedResources, MedicationRequest medicationRequest, Date scheduledStartDate, Date endDate) {
        String id = medicationRequest.getId();
        Provenance provenance = (Provenance) getFhirResourceById(id + "-provenance", mappedResources).getResource();
        assertTrue(((Reference) provenance.getAgent().get(0).getWho()).getReference().endsWith("321.json"));
        assertEquals(provenance.getTargetFirstRep().getReference(), id);
        assertEquals(provenance.getRecorded(), medicationRequest.getAuthoredOn());

        assertEquals(scheduledStartDate, provenance.getPeriod().getStart());
        if (null == endDate) {
            assertNull(provenance.getPeriod().getEnd());
        } else {
            assertEquals(endDate, provenance.getPeriod().getEnd());
        }

        Coding activity = provenance.getActivity();
        assertEquals(FHIR_DATA_OPERATION_VALUESET_URL, activity.getSystem());
        assertEquals(FHIR_DATA_OPERATION_CREATE_CODE, activity.getCode());
    }

    private FHIRResource getFhirResourceById(String id, List<FHIRResource> mappedResources) {
        return mappedResources.stream().filter(fhirResource -> fhirResource.getResource().getId().endsWith(id)).findFirst().orElse(null);
    }

}
