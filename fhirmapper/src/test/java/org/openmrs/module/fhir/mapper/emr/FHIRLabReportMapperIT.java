package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
public class FHIRLabReportMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private PatientService patientService;
    @Autowired
    private FHIRLabReportMapper diagnosticReportMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private IdMappingRepository idMappingRepository;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/labResultDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleReportWithoutCategory() throws Exception {
        assertTrue(diagnosticReportMapper.canHandle(new DiagnosticReport()));
    }

    @Test
    public void shouldHandleReportWithLabCategory() throws Exception {
        DiagnosticReport report = new DiagnosticReport();
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
                .setSystem(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL)
                .setCode(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE)
                .setDisplay(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_DISPLAY);
        report.setCategory(category);
        assertTrue(diagnosticReportMapper.canHandle(report));
    }

    @Test
    public void shouldNotHandleReportWithOtherCategory() throws Exception {
        DiagnosticReport report = new DiagnosticReport();
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
                .setSystem(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL)
                .setCode("RAD")
                .setDisplay("Radiology");
        report.setCategory(category);
        assertFalse(diagnosticReportMapper.canHandle(report));
    }

    @Test
    public void shouldMapDiagnosticReportForTestResultWithEncounterRequestDetail() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReport.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));

        ShrEncounterBundle shrEncounterBundle = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        diagnosticReportMapper.map(report, emrEncounter, shrEncounterBundle, getSystemProperties("1"));
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs topLevelObs = obsSet.iterator().next();
        Concept hemoglobinConcept = conceptService.getConcept(303);
        assertEquals(hemoglobinConcept, topLevelObs.getConcept());
        Order testOrder = orderService.getOrder(50);
        assertEquals(testOrder, topLevelObs.getOrder());

        assertTestObsWithNumericResult(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    @Test
    public void shouldProcessPanelResults() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithPanelReport.xml", springContext);
        List<Resource> resources = FHIRBundleHelper.identifyResourcesByName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));
        for (Resource resource : resources) {
            DiagnosticReport report = (DiagnosticReport) resource;
            ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
            diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));
        }
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs panelObs = obsSet.iterator().next();
        Concept panelConcept = conceptService.getConcept(302);
        assertEquals(panelConcept, panelObs.getConcept());
        Order testOrder = orderService.getOrder(51);
        assertEquals(testOrder, panelObs.getOrder());
        assertEquals(2, panelObs.getGroupMembers().size());

        Concept hemoglobinConcept = conceptService.getConcept(303);
        Obs hemoglobinObs = findObsByConcept(panelObs.getGroupMembers(), hemoglobinConcept);
        assertNotNull(hemoglobinObs);
        assertTestObsWithNumericResult(hemoglobinObs, hemoglobinConcept, 20.0, null, testOrder);

        Concept esrConcept = conceptService.getConcept(304);
        Obs esrObs = findObsByConcept(panelObs.getGroupMembers(), esrConcept);
        assertNotNull(esrObs);
        assertTestObsWithNumericResult(esrObs, esrConcept, 20.0, null, testOrder);
    }

    @Test
    public void shouldUpdateTestResultsIfUpdatedAndAssociateWithActiveOrder() throws Exception {
        Encounter existingEncounter = encounterService.getEncounter(18);
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithUpdatedTestResult.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());

        EmrEncounter emrEncounter = new EmrEncounter(existingEncounter);
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shrEncounterId5");

        Concept hemoglobinConcept = conceptService.getConcept(303);
        Order activeTestOrder = orderService.getOrder(54);

        Set<Obs> existingObsGroup = existingEncounter.getObsAtTopLevel(false);
        assertEquals(1, existingObsGroup.size());
        assertTestObsWithNumericResult(existingObsGroup.iterator().next(), hemoglobinConcept, 120.0, null, activeTestOrder);

        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));

        assertEquals(1, emrEncounter.getTopLevelObs().size());
        Set<Obs> updatedObsGroup = emrEncounter.getTopLevelObs();
        assertEquals(1, updatedObsGroup.size());
        assertTestObsWithNumericResult(updatedObsGroup.iterator().next(), hemoglobinConcept, 20.0, null, activeTestOrder);
    }

    @Test
    public void shouldNotAssociateWithOrderIfOrderNotFound() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReportWithoutRequestDetail.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs topLevelObs = obsSet.iterator().next();
        Concept hemoglobinConcept = conceptService.getConcept(303);
        assertEquals(hemoglobinConcept, topLevelObs.getConcept());
        assertNull(topLevelObs.getOrder());

        assertTestObsWithNumericResult(topLevelObs, hemoglobinConcept, 20.0, "changed", null);
    }

    @Test
    public void shouldMapDiagnosticReportForTestResultWithOrderRequestDetail() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReportWithOrderRequestDetail.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs topLevelObs = obsSet.iterator().next();
        Concept hemoglobinConcept = conceptService.getConcept(303);
        assertEquals(hemoglobinConcept, topLevelObs.getConcept());
        Order testOrder = orderService.getOrder(55);
        assertEquals(testOrder, topLevelObs.getOrder());

        assertTestObsWithNumericResult(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    @Test
    public void shouldMapDiagnosticReportForLocalPanelOrder() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReportForLocalPanelOrder.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));
        //Multiple orders present for the request detail.
        assertEquals(3, idMappingRepository.findMappingsByExternalId("shrEncounterId22:6daaes86-efab-ls29-sow2-f15206e63ab0", IdMappingType.DIAGNOSTIC_ORDER).size());

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs topLevelObs = obsSet.iterator().next();
        Concept hemoglobinConcept = conceptService.getConcept(303);
        assertEquals(hemoglobinConcept, topLevelObs.getConcept());
        Order testOrder = orderService.getOrder(57);
        assertEquals(testOrder, topLevelObs.getOrder());

        assertTestObsWithNumericResult(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    @Test
    public void shouldMapDiagnosticReportForLocalTestConcept() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReportWithLocalTest.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(1, obsSet.size());
        Obs topLevelObs = obsSet.iterator().next();
        Concept localTestConcept = conceptService.getConceptByName("WBC" + MRSProperties.UNVERIFIED_BY_TR);
        assertEquals(localTestConcept, topLevelObs.getConcept());
        assertNull(topLevelObs.getOrder());

        assertTestObsWithTextResult(topLevelObs, localTestConcept, "20.0", "changed", null);
    }

    @Test
    public void shouldMapDiagnosticReportForLocalTestAndTRPanel() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/stu3/diagnosticReportWithLocalTestAndTRPanelResult.xml", springContext);
        List<Resource> resources = FHIRBundleHelper.identifyResourcesByName(bundle, new DiagnosticReport().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1");
        for (Resource resource : resources) {
            diagnosticReportMapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));
        }
        Set<Obs> obsSet = emrEncounter.getTopLevelObs();
        assertEquals(2, obsSet.size());

        Concept localTestConcept = conceptService.getConceptByName("WBC" + MRSProperties.UNVERIFIED_BY_TR);
        Obs localTestObs = findObsByConcept(obsSet, localTestConcept);
        assertNull(localTestObs.getOrder());
        assertTestObsWithTextResult(localTestObs, localTestConcept, "20.0", "changed", null);

        Concept trPanelConcept = conceptService.getConcept(302);
        Obs trPanelObs = findObsByConcept(obsSet, trPanelConcept);
        Order panelOrder = orderService.getOrder(16);
        assertEquals(panelOrder, trPanelObs.getOrder());
        assertEquals(1, trPanelObs.getGroupMembers().size());
        Concept hemoglobinConcept = conceptService.getConcept(303);
        Obs hemoglobinObs = findObsByConcept(trPanelObs.getGroupMembers(), hemoglobinConcept);
        assertNotNull(hemoglobinObs);
        assertTestObsWithNumericResult(hemoglobinObs, hemoglobinConcept, 20.0, null, panelOrder);
    }

    private void assertTestObsWithNumericResult(Obs topLevelObs, Concept resultObsConcept, Double resultValueNumeric, String notesValue, Order testOrder) {
        Obs resultObsGroupObs = assertTestObs(topLevelObs, notesValue, testOrder);

        if (resultValueNumeric != null) {
            Obs resultObs = findObsByConcept(resultObsGroupObs.getGroupMembers(), resultObsConcept);
            assertNotNull(resultObs);
            assertEquals(testOrder, resultObs.getOrder());
            assertEquals(resultValueNumeric, resultObs.getValueNumeric());
        }
    }

    private void assertTestObsWithTextResult(Obs topLevelObs, Concept resultObsConcept, String resultValue, String notesValue, Order testOrder) {
        Obs resultObsGroupObs = assertTestObs(topLevelObs, notesValue, testOrder);

        if (resultValue != null) {
            Obs resultObs = findObsByConcept(resultObsGroupObs.getGroupMembers(), resultObsConcept);
            assertNotNull(resultObs);
            assertEquals(testOrder, resultObs.getOrder());
            assertEquals(resultValue, resultObs.getValueText());
        }
    }

    private Obs assertTestObs(Obs topLevelObs, String notesValue, Order testOrder) {
        assertEquals(testOrder, topLevelObs.getOrder());
        Set<Obs> resultObsGroupMembers = topLevelObs.getGroupMembers();
        assertEquals(1, resultObsGroupMembers.size());

        Obs resultObsGroupObs = resultObsGroupMembers.iterator().next();
        assertEquals(testOrder, resultObsGroupObs.getOrder());


        if (notesValue != null) {
            Concept labNotesConcept = conceptService.getConcept(103);
            Obs notesObs = findObsByConcept(resultObsGroupObs.getGroupMembers(), labNotesConcept);
            assertNotNull(notesObs);
            assertEquals(testOrder, notesObs.getOrder());
            assertEquals(notesValue, notesObs.getValueText());
        }
        return resultObsGroupObs;
    }

    private Obs findObsByConcept(Set<Obs> obsSet, Concept concept) {
        for (Obs obs : obsSet) {
            if (obs.getConcept().equals(concept)) {
                return obs;
            }
        }
        return null;
    }
}