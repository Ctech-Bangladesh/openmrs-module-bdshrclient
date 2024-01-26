package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;
import static org.openmrs.module.fhir.TestFhirFeedHelper.getFirstResourceByType;
import static org.openmrs.module.fhir.TestFhirFeedHelper.getResourceByReference;
import static org.openmrs.module.fhir.utils.DateUtil.parseDate;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProcedureFulfillmentMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ProcedureFulfillmentMapper procedureFulfillmentMapper;
    @Autowired
    private ObsService obsService;

    private final String patientRef = "http://mci.com/patients/HID-123";
    private final String fhirEncounterId = "SHR-ENC1";

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/procedureFulfillmentDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleProcedureOrderFulfillment() throws Exception {
        Obs fulfilmentObs = obsService.getObs(1011);
        assertTrue(procedureFulfillmentMapper.canHandle(fulfilmentObs));
    }

    @Test
    public void shouldNotHandleIfFulfilmentIsNotHavingProcedureTemplate() throws Exception {
        Obs fulfilmentObs = obsService.getObs(1000);
        assertFalse(procedureFulfillmentMapper.canHandle(fulfilmentObs));
    }

    @Test
    public void shouldMapAProcedureOrderFulfillment() throws Exception {
        Obs obs = obsService.getObs(1011);
        List<FHIRResource> resources = procedureFulfillmentMapper.map(obs, createFhirEncounter(), getSystemProperties("1"));

        assertFalse(resources.isEmpty());
        assertEquals(1, resources.size());
        Resource resource = resources.get(0).getResource();
        assertTrue(resource instanceof Procedure);
        Procedure procedure = (Procedure) resource;

        assertProcedureWithNotes(procedure, obs, Procedure.ProcedureStatus.INPROGRESS, "Procedure went well");
        assertProcedureOutcome(procedure);
        assertProcedureFollowup(procedure);
        assertProcedurePerformed(procedure);
    }

    @Test
    public void shouldNotMapIfProcedureTypeIsNotPresent() throws Exception {
        Obs fulfillmentObs = obsService.getObs(1499);
        List<FHIRResource> resources = procedureFulfillmentMapper.map(fulfillmentObs, createFhirEncounter(), getSystemProperties("1"));

        assertTrue(resources.isEmpty());
    }

    @Test
    public void shouldMapProcedureOrderFulfillmentWithDiagnosisStudy() throws Exception {
        Obs fulfillmentObs = obsService.getObs(1099);
        List<FHIRResource> resources = procedureFulfillmentMapper.map(fulfillmentObs, createFhirEncounter(), getSystemProperties("1"));

        assertEquals(3, resources.size());
        Procedure procedure = (Procedure) getFirstResourceByType(new Procedure().getResourceType().name(), resources).getResource();
        assertProcedureWithNotes(procedure, fulfillmentObs, Procedure.ProcedureStatus.INPROGRESS, "Procedure went well");
        assertProcedureOutcome(procedure);
        assertProcedureFollowup(procedure);
        assertProcedurePerformed(procedure);

        List<Reference> reports = procedure.getReport();
        assertEquals(1, reports.size());
        Reference reportReference = reports.get(0);
        DiagnosticReport report = (DiagnosticReport) TestFhirFeedHelper.getResourceByReference(reportReference, resources).getResource();

        assertDiagnosticReport(report);

        List<Reference> reportResult = report.getResult();
        assertEquals(1, reportResult.size());
        Reference resultReference = reportResult.get(0);
        Observation result = (Observation) getResourceByReference(resultReference, resources).getResource();

        assertProcedureResult(result);
    }

    @Test
    public void shouldMapProcedureOrderFulfillmentHavingJustProcedureType() throws Exception {
        Obs fulfillmentObs = obsService.getObs(1501);
        List<FHIRResource> resources = procedureFulfillmentMapper.map(fulfillmentObs, createFhirEncounter(), getSystemProperties("1"));

        assertEquals(1, resources.size());
        Procedure procedure = (Procedure) resources.get(0).getResource();
        assertProcedureWithoutNotes(procedure, fulfillmentObs, Procedure.ProcedureStatus.COMPLETED);
        assertTrue(procedure.getOutcome().isEmpty());
        assertTrue(procedure.getFollowUp().isEmpty());
        Period performed = (Period) procedure.getPerformed();
        assertNull(performed.getStart());
        assertNull(performed.getEnd());
    }

    @Test
    public void shouldMapAUpdateToFulfillmentAsNewProcedure() throws Exception {
        Obs fulfillmentObs = obsService.getObs(1600);
        List<FHIRResource> resources = procedureFulfillmentMapper.map(fulfillmentObs, createFhirEncounter(), getSystemProperties("1"));

        assertFalse(resources.isEmpty());
        assertEquals(1, resources.size());
        Resource resource = resources.get(0).getResource();
        assertTrue(resource instanceof Procedure);
        Procedure procedure = (Procedure) resource;

        assertProcedureWithNotes(procedure, fulfillmentObs, Procedure.ProcedureStatus.INPROGRESS, "Procedure not completed");
        assertTrue(procedure.getOutcome().isEmpty());
        assertProcedureFollowup(procedure);
        assertProcedurePerformed(procedure);

        Obs updatedFulfillmentObs = obsService.getObs(1700);
        List<FHIRResource> updatedResources = procedureFulfillmentMapper.map(updatedFulfillmentObs, createFhirEncounter(), getSystemProperties("1"));

        assertFalse(updatedResources.isEmpty());
        assertEquals(1, updatedResources.size());
        Resource newResource = updatedResources.get(0).getResource();
        assertTrue(newResource instanceof Procedure);
        Procedure newProcedure = (Procedure) newResource;

        assertProcedureWithNotes(newProcedure, updatedFulfillmentObs, Procedure.ProcedureStatus.COMPLETED, "Procedure Finished");
        assertProcedureOutcome(newProcedure);
        assertTrue(newProcedure.getFollowUp().isEmpty());
        assertProcedurePerformed(newProcedure);
    }

    private void assertProcedureWithNotes(Procedure procedure, Obs obs, Procedure.ProcedureStatus procedureStatus, String procedureNotes) {
        assertProcedureWithoutNotes(procedure, obs, procedureStatus);
        assertEquals(procedureNotes, procedure.getNote().get(0).getText());
    }

    private void assertProcedureWithoutNotes(Procedure procedure, Obs obs, Procedure.ProcedureStatus procedureStatus) {
        assertNotNull(procedure.getIdentifier());
        assertNotNull(procedure.getId());
        assertEquals(1, procedure.getIdentifier().size());
        assertTrue(procedure.getId().contains(obs.getUuid()));
        assertEquals(patientRef, procedure.getSubject().getReference());
        assertEquals(fhirEncounterId, procedure.getContext().getReference());
        List<Coding> procedureType = procedure.getCode().getCoding();
        assertTrue(containsCoding(procedureType, "Osteopathic-Treatment-of-Abdomen", "http://tr.com/Osteopathic-Treatment-of-Abdomen", "ProcedureAnswer1"));
        assertEquals(procedureStatus, procedure.getStatus());
        assertEquals("http://shr.com/patients/HID/encounters/shr-enc-1#ProcedureRequest/procedure_req_id", procedure.getBasedOnFirstRep().getReference());
    }

    private void assertProcedurePerformed(Procedure procedure) {
        Period performed = (Period) procedure.getPerformed();
        assertEquals(parseDate("2015-01-10 00:00:00"), performed.getStart());
        assertEquals(parseDate("2015-01-15 00:00:00"), performed.getEnd());
    }

    private void assertProcedureFollowup(Procedure procedure) {
        List<Coding> procedureFollowup = procedure.getFollowUpFirstRep().getCoding();
        assertTrue(containsCoding(procedureFollowup, "6831", "http://tr.com/6831", "Change of dressing"));
    }

    private void assertProcedureOutcome(Procedure procedure) {
        List<Coding> procedureOutcome = procedure.getOutcome().getCoding();
        assertTrue(containsCoding(procedureOutcome, "385669000", "http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Procedure-Outcome", "Successful"));
    }

    private void assertDiagnosticReport(DiagnosticReport report) {
        assertEquals(fhirEncounterId, report.getContext().getReference());
        assertEquals(patientRef, report.getSubject().getReference());
        assertEquals(DiagnosticReport.DiagnosticReportStatus.FINAL, report.getStatus());
        assertEquals(parseDate("2010-08-01 15:09:05"), report.getIssued());
        assertTrue(report.getId().contains("ew6574cb-22yy-891a-giz7-3450552c77459"));
        List<Coding> reportCoding = report.getCode().getCoding();
        assertTrue(containsCoding(reportCoding, "Test A-LOINC", "http://tr.com/Test-A-LOINC", "Test A"));
        List<Coding> procedureDiagnosisCoding = report.getCodedDiagnosis().get(0).getCoding();
        assertTrue(containsCoding(procedureDiagnosisCoding, "J19.406475", "http://tr.com/Viral-Pneumonia-LOINC", "Viral pneumonia 406475"));
    }

    private void assertProcedureResult(Observation result) {
        assertTrue(result.getId().contains("dia574cb-22yy-671a-giz7-3450552cresult"));
        assertTrue(containsCoding(result.getCode().getCoding(), "Test A-LOINC", "http://tr.com/Test-A-LOINC", "Test A"));
        assertEquals("Blood Pressure is very high", ((StringType) result.getValue()).getValue());
    }

    private FHIREncounter createFhirEncounter() {
        Encounter encounter = new Encounter();
        encounter.setSubject(new Reference(patientRef));
        encounter.setId(fhirEncounterId);
        return new FHIREncounter(encounter);
    }

}