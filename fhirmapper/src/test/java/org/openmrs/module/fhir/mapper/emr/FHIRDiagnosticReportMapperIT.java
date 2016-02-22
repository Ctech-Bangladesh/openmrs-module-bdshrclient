package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticReport;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
public class FHIRDiagnosticReportMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private PatientService patientService;
    @Autowired
    private FHIRDiagnosticReportMapper diagnosticReportMapper;
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
    public void shouldMapDiagnosticReportForTestResultWithEncounterRequestDetail() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/dstu2/diagnosticReport.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceName());
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
        Order testOrder = orderService.getOrder(50);
        assertEquals(testOrder, topLevelObs.getOrder());

        assertTestObs(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    @Test
    public void shouldProcessPanelResults() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithPanelReport.xml", springContext);
        List<IResource> resources = FHIRBundleHelper.identifyResourcesByName(bundle, new DiagnosticReport().getResourceName());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        encounter.setPatient(patientService.getPatient(1));
        for (IResource resource : resources) {
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
        assertTestObs(hemoglobinObs, hemoglobinConcept, 20.0, null, testOrder);

        Concept esrConcept = conceptService.getConcept(304);
        Obs esrObs = findObsByConcept(panelObs.getGroupMembers(), esrConcept);
        assertNotNull(esrObs);
        assertTestObs(esrObs, esrConcept, 20.0, null, testOrder);
    }

    @Test
    public void shouldUpdateTestResultsIfUpdatedAndAssociateWithActiveOrder() throws Exception {
        Encounter existingEncounter = encounterService.getEncounter(18);
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithUpdatedTestResult.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceName());

        EmrEncounter emrEncounter = new EmrEncounter(existingEncounter);
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98101039678", "shrEncounterId5");

        Concept hemoglobinConcept = conceptService.getConcept(303);
        Order activeTestOrder = orderService.getOrder(54);

        Set<Obs> existingObsGroup = existingEncounter.getObsAtTopLevel(false);
        assertEquals(1, existingObsGroup.size());
        assertTestObs(existingObsGroup.iterator().next(), hemoglobinConcept, 120.0, null, activeTestOrder);

        diagnosticReportMapper.map(report, emrEncounter, encounterComposition, getSystemProperties("1"));

        assertEquals(1, emrEncounter.getTopLevelObs().size());
        Set<Obs> updatedObsGroup = emrEncounter.getTopLevelObs();
        assertEquals(1, updatedObsGroup.size());
        assertTestObs(updatedObsGroup.iterator().next(), hemoglobinConcept, 20.0, null, activeTestOrder);
    }

    @Test
    public void shouldNotAssociateWithOrderIfOrderNotFound() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/dstu2/diagnosticReportWithoutRequestDetail.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceName());
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

        assertTestObs(topLevelObs, hemoglobinConcept, 20.0, "changed", null);
    }

    @Test
    public void shouldMapDiagnosticReportForTestResultWithOrderRequestDetail() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/dstu2/diagnosticReportWithOrderRequestDetail.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceName());
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

        assertTestObs(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    @Test
    public void shouldMapDiagnosticReportForLocalPanelOrder() throws Exception {
        Bundle bundle = (Bundle) new MapperTestHelper()
                .loadSampleFHIREncounter("encounterBundles/dstu2/diagnosticReportForLocalPanelOrder.xml", springContext);
        DiagnosticReport report = (DiagnosticReport) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new DiagnosticReport().getResourceName());
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

        assertTestObs(topLevelObs, hemoglobinConcept, 20.0, "changed", testOrder);
    }

    private void assertTestObs(Obs topLevelObs, Concept resultObsConcept, Double resultValueNumeric, String notesValue, Order testOrder) {
        assertEquals(testOrder, topLevelObs.getOrder());
        Set<Obs> resultObsGroupMembers = topLevelObs.getGroupMembers();
        assertEquals(1, resultObsGroupMembers.size());

        Obs resultObsGroupObs = resultObsGroupMembers.iterator().next();
        assertEquals(testOrder, resultObsGroupObs.getOrder());

        if (resultValueNumeric != null) {
            Obs resultObs = findObsByConcept(resultObsGroupObs.getGroupMembers(), resultObsConcept);
            assertNotNull(resultObs);
            assertEquals(testOrder, resultObs.getOrder());
            assertEquals(resultValueNumeric, resultObs.getValueNumeric());
        }

        if (notesValue != null) {
            Concept labNotesConcept = conceptService.getConcept(103);
            Obs notesObs = findObsByConcept(resultObsGroupObs.getGroupMembers(), labNotesConcept);
            assertNotNull(notesObs);
            assertEquals(testOrder, notesObs.getOrder());
            assertEquals(notesValue, notesObs.getValueText());
        }
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