package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;
import static org.openmrs.module.fhir.TestFhirFeedHelper.getResourceByReference;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProcedureMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ObsService obsService;

    @Autowired
    private ProcedureMapper procedureMapper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/procedureDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleProcedureTypeObservation() {
        Obs observation = obsService.getObs(1100);
        assertTrue(procedureMapper.canHandle(observation));
    }

    @Test
    public void shouldNotMapIfProcedureTypeIsNotPresent() throws Exception {
        Obs obs = obsService.getObs(1500);
        final Encounter fhirEncounter = new Encounter();
        List<FHIRResource> fhirResources = procedureMapper.map(obs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
        assertTrue(CollectionUtils.isEmpty(fhirResources));
    }

    @Test
    public void shouldNotMapDiagnosisReportIfDiagnosticTestIsNotPresent() throws Exception {
        Obs obs = obsService.getObs(1501);
        final Encounter fhirEncounter = new Encounter();
        List<FHIRResource> fhirResources = procedureMapper.map(obs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
        assertTrue(fhirResources.size() == 1);
        assertTrue(fhirResources.get(0).getResource() instanceof Procedure);
    }

    @Test
    public void shouldMapPatientAndEncounter() {
        Encounter fhirEncounter = new Encounter();
        Reference patient = new Reference();
        patient.setReference("Hid");
        fhirEncounter.setSubject(patient);
        Encounter.EncounterParticipantComponent participant = new Encounter.EncounterParticipantComponent();
        Reference resourceReferenceDt = new Reference("10");
        participant.setIndividual(resourceReferenceDt);
        fhirEncounter.addParticipant(participant);

        List<FHIRResource> fhirResources = mapProcedure(1100, fhirEncounter);
        assertEquals(3, fhirResources.size());
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), fhirResources).getResource();

        assertEquals(patient, procedure.getSubject());

        assertEquals(fhirEncounter.getId(), procedure.getContext().getReference());

        assertEquals(1, procedure.getPerformer().size());
        assertEquals(resourceReferenceDt, procedure.getPerformer().get(0).getActor());
    }

    @Test
    public void shouldPopulateReferencesAndIds() {
        List<FHIRResource> fhirResources = mapProcedure(1100, new Encounter());
        assertEquals(3, fhirResources.size());

        FHIRResource procedureResource = TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), fhirResources);
        assertTrue(procedureResource.getResource() instanceof Procedure);
        assertEquals("urn:uuid:ef4554cb-22gg-471a-lld7-1434552c337c1", procedureResource.getIdentifierList().get(0).getValue());
        assertEquals("urn:uuid:ef4554cb-22gg-471a-lld7-1434552c337c1", procedureResource.getResource().getId());
        assertEquals("Procedure", procedureResource.getResourceName());

        FHIRResource diagnosticReportResource = TestFhirFeedHelper.getFirstResourceByType(new DiagnosticReport().getResourceType().name(), fhirResources);
        assertNotNull(diagnosticReportResource);
        Coding categoryCoding = ((DiagnosticReport) diagnosticReportResource.getResource()).getCategory().getCodingFirstRep();
        assertEquals(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL, categoryCoding.getSystem());
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_CODE, categoryCoding.getCode());
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_DISPLAY, categoryCoding.getDisplay());

        assertEquals("urn:uuid:ew6574cb-22yy-891a-giz7-3450552c77459", diagnosticReportResource.getIdentifierList().get(0).getValue());
        assertEquals("urn:uuid:ew6574cb-22yy-891a-giz7-3450552c77459", diagnosticReportResource.getResource().getId());
        assertEquals("Diagnostic Report", diagnosticReportResource.getResourceName());

        FHIRResource resultResource = TestFhirFeedHelper.getFirstResourceByType(new Observation().getResourceType().name(), fhirResources);
        assertNotNull(resultResource);
        assertEquals("urn:uuid:dia574cb-22yy-671a-giz7-3450552cresult", resultResource.getIdentifierList().get(0).getValue());
        assertEquals("Test A", resultResource.getResourceName());
    }

    @Test
    public void shouldMapProcedureType() throws Exception {
        List<FHIRResource> fhirResources = mapProcedure(1100, buildEncounter());
        assertEquals(3, fhirResources.size());

        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), fhirResources).getResource();
        Coding procedureType = procedure.getCode().getCoding().get(0);
        assertNotNull(procedureType);
        assertEquals("ProcedureAnswer1", procedureType.getDisplay());
        assertEquals("http://tr.com/Osteopathic-Treatment-of-Abdomen", procedureType.getSystem());
        assertEquals("Osteopathic-Treatment-of-Abdomen", procedureType.getCode());
    }

    @Test
    public void shouldMapOutCome() {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, new Encounter())).getResource();
        assertEquals("385669000", procedure.getOutcome().getCodingFirstRep().getCode());
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Procedure-Outcome", procedure.getOutcome().getCodingFirstRep().getSystem());
        assertEquals("Successful", procedure.getOutcome().getCodingFirstRep().getDisplay());
    }

    @Test
    public void shouldMapFollowUp() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, buildEncounter())).getResource();
        assertEquals(1, procedure.getFollowUp().size());
        CodeableConcept followUpCode = procedure.getFollowUp().get(0);
        assertEquals(2, followUpCode.getCoding().size());
        assertTrue(containsCoding(followUpCode.getCoding(), "18949003", "http://tr.com/12345", "Change of dressing"));
        assertTrue(containsCoding(followUpCode.getCoding(), "6831", "http://tr.com/6831", "Change of dressing"));
    }

    @Test
    public void shouldMapPeriod() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, buildEncounter())).getResource();
        Period period = (Period) procedure.getPerformed();

        Date expectedStartDate = DateUtil.parseDate("2015-01-10 00:00:00");
        Date expectedEndDate = DateUtil.parseDate("2015-01-15 00:00:00");

        assertEquals(expectedStartDate, period.getStart());
        assertEquals(expectedEndDate, period.getEnd());
    }

    @Test
    public void shouldMapProcedureNotes() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, buildEncounter())).getResource();
        assertEquals("Procedure went well", procedure.getNote().get(0).getText());
    }

    @Test
    public void shouldMapProcedureStatus() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, buildEncounter())).getResource();
        assertEquals(Procedure.ProcedureStatus.INPROGRESS, procedure.getStatusElement().getValue());
    }

    @Test
    public void shouldSetProcedureStatusAsCompletedIfNotGiven() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1501, buildEncounter())).getResource();
        assertEquals(Procedure.ProcedureStatus.COMPLETED, procedure.getStatusElement().getValue());
    }

    @Test
    public void shouldMapDiagnosisReport() throws Exception {
        Encounter fhirEncounter = buildEncounter();
        List<FHIRResource> fhirResources = mapProcedure(1100, fhirEncounter);
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), fhirResources).getResource();

        assertEquals(1, procedure.getReport().size());
        Reference reportReference = procedure.getReport().get(0);
        Resource diagnosticReportResource = getResourceByReference(reportReference, fhirResources).getResource();
        assertNotNull(diagnosticReportResource);
        assertTrue(diagnosticReportResource instanceof DiagnosticReport);
        DiagnosticReport diagnosticReport = (DiagnosticReport) diagnosticReportResource;

        assertEquals(fhirEncounter.getId(), diagnosticReport.getContext().getReference());
        assertEquals("patient", diagnosticReport.getSubject().getReference());
        assertEquals("Provider 1", diagnosticReport.getPerformer().get(0).getActor().getReference());
        Date expectedDate = DateUtil.parseDate("2010-08-18 15:09:05");
        Date diagnosticDate = ((DateTimeType) diagnosticReport.getEffective()).getValue();
        assertEquals(expectedDate, diagnosticDate);

        assertTestCoding(diagnosticReport.getCode().getCoding());
        assertCodedDiagnosis(diagnosticReport);

        assertEquals(1, diagnosticReport.getResult().size());
        Observation resultResource = (Observation) getResourceByReference(diagnosticReport.getResult().get(0), fhirResources).getResource();
        assertDiagnosisResult(resultResource, fhirEncounter);
    }

    @Test
    public void shouldNotMapReferenceTermWhenMapTypeIsNotSAMEAS() throws Exception {
        Procedure procedure = (Procedure) TestFhirFeedHelper.getFirstResourceByType(new Procedure().getResourceType().name(), mapProcedure(1100, buildEncounter())).getResource();
        assertEquals(1, procedure.getFollowUp().size());
        CodeableConcept followUpCode = procedure.getFollowUp().get(0);
        assertEquals(2, followUpCode.getCoding().size());
        assertTrue(containsCoding(followUpCode.getCoding(), "18949003", "http://tr.com/12345", "Change of dressing"));
        assertTrue(containsCoding(followUpCode.getCoding(), "6831", "http://tr.com/6831", "Change of dressing"));
    }

    private void assertDiagnosisResult(Observation result, Encounter fhirEncounter) {
        assertNotNull(result);
        assertEquals(fhirEncounter.getId(), result.getContext().getReference());
        assertEquals("patient", result.getSubject().getReference());
        assertTrue(result.getValue() instanceof StringType);
        assertEquals("Blood Pressure is very high", ((StringType) result.getValue()).getValue());
        assertEquals(Observation.ObservationStatus.FINAL, result.getStatusElement().getValue());
        assertTestCoding(result.getCode().getCoding());
    }

    private void assertCodedDiagnosis(DiagnosticReport diagnosticReport) {
        assertTrue(diagnosticReport.getCodedDiagnosis().size() == 1);
        List<Coding> codings = diagnosticReport.getCodedDiagnosis().get(0).getCoding();
        assertTrue(codings.size() == 2);

        assertTrue(containsCoding(codings, "J19.406475", "http://tr.com/Viral-Pneumonia-LOINC", "Viral pneumonia 406475"));
        assertTrue(containsCoding(codings, "Viral-Pneumonia", "http://tr.com/Viral-Pneumonia", "Viral pneumonia 406475"));
    }

    private void assertTestCoding(List<Coding> codings) {
        assertTrue(codings.size() == 2);
        assertTrue(containsCoding(codings, "Test A-LOINC", "http://tr.com/Test-A-LOINC", "Test A"));
        assertTrue(containsCoding(codings, "Test A", "http://tr.com/Test-A", "Test A"));
    }

    private List<FHIRResource> mapProcedure(int observationId, Encounter fhirEncounter) {
        Obs obs = obsService.getObs(observationId);
        return procedureMapper.map(obs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
    }

    private Encounter buildEncounter() {
        Encounter fhirEncounter = new Encounter();
        fhirEncounter.setSubject(new Reference().setReference("patient"));
        Encounter.EncounterParticipantComponent participant = fhirEncounter.addParticipant();
        participant.setIndividual(new Reference().setReference("Provider 1"));
        fhirEncounter.setId("urn:uuid:6d0af6767-707a-4629-9850-f15206e63ab0");
        return fhirEncounter;
    }
}
