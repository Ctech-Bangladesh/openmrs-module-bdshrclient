package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.Date;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRDiagnosisConditionMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private FHIRDiagnosisConditionMapper diagnosisConditionMapper;

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Autowired
    private EncounterService encounterService;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/diagnosisTestDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleDiagnosisCondition() throws Exception {
        Bundle bundle = loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithDiagnosisCondition.xml");
        Condition diagnosisCondition = getCondition(bundle);
        assertTrue(diagnosisConditionMapper.canHandle(diagnosisCondition));
    }

    @Test
    public void shouldNotHandleChiefComplaintCondition() throws Exception {
        Bundle bundle = loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithChiefComplaints.xml");
        Condition chiefComplaintCondition = getCondition(bundle);
        assertFalse(diagnosisConditionMapper.canHandle(chiefComplaintCondition));
    }

    @Test
    public void shouldMapADiagnosisCondition() throws Exception {
        EmrEncounter emrEncounter = mapDiagnosis("encounterBundles/dstu2/encounterWithDiagnosisCondition.xml");
        String conditionUuid = "35b57256-f229-476e-b5a1-c73af110485d";

        Set<Obs> topLevelObs = emrEncounter.getTopLevelObs();
        assertEquals(1, topLevelObs.size());
        Obs visitDiagnosisObs = topLevelObs.iterator().next();
        assertVisitDiagnosis(visitDiagnosisObs, "HID-123", "SHR_ENC_ID", conditionUuid, "Updated Comment.");

        Set<Obs> visitDiagnosisMembers = visitDiagnosisObs.getGroupMembers();
        assertCodedDiagnosis(400, visitDiagnosisMembers);
        assertDiagnosisOrder(406, visitDiagnosisMembers);
        assertDiagnosisCertainty(407, visitDiagnosisMembers);
        assertInitialDiagnosis(visitDiagnosisObs.getUuid(), visitDiagnosisMembers);
        assertDiagnosisRevised(8, visitDiagnosisMembers);
    }

    @Test
    public void shouldNotMapWhenTheAnswerConceptIsNotPresent() throws Exception {
        EmrEncounter emrEncounter = mapDiagnosis("encounterBundles/dstu2/encounterWithDiagnosisConditionHavingNotSyncedConcept.xml");
        Set<Obs> topLevelObs = emrEncounter.getTopLevelObs();
        assertTrue(topLevelObs.isEmpty());
    }

    @Test
    public void shouldUpdateADiagnosis() throws Exception {
        Encounter encounter = encounterService.getEncounter(39);
        Set<Obs> obsAtTopLevel = encounter.getObsAtTopLevel(false);
        assertEquals(1, obsAtTopLevel.size());
        Obs visitDiagnosisObs = obsAtTopLevel.iterator().next();

        String conditionUuid = "35b57256-f229-476e-b5a1-c73af110485d";
        String healthId = "1234512345123";
        String shrEncounterId = "shr_enc_id_2";
        assertVisitDiagnosis(visitDiagnosisObs, healthId, shrEncounterId, conditionUuid, "Some Comment");
        Set<Obs> visitDiagnosisMembers = visitDiagnosisObs.getGroupMembers();
        assertCodedDiagnosis(400, visitDiagnosisMembers);
        assertDiagnosisOrder(406, visitDiagnosisMembers);
        assertDiagnosisCertainty(417, visitDiagnosisMembers);
        assertInitialDiagnosis(visitDiagnosisObs.getUuid(), visitDiagnosisMembers);
        assertDiagnosisRevised(8, visitDiagnosisMembers);

        EmrEncounter emrEncounter = mapDiagnosis("encounterBundles/dstu2/encounterWithDiagnosisCondition.xml", encounter, healthId, shrEncounterId);

        Set<Obs> topLevelObs = emrEncounter.getTopLevelObs();
        assertEquals(1, topLevelObs.size());
        Obs updatedVisitDiagnosisObs = topLevelObs.iterator().next();
        assertVisitDiagnosis(updatedVisitDiagnosisObs, healthId, shrEncounterId, conditionUuid, "Updated Comment.");

        Set<Obs> updatedVisitDiagnosisMembers = updatedVisitDiagnosisObs.getGroupMembers();
        assertCodedDiagnosis(400, updatedVisitDiagnosisMembers);
        assertDiagnosisOrder(406, updatedVisitDiagnosisMembers);
        assertDiagnosisCertainty(407, updatedVisitDiagnosisMembers);
        assertInitialDiagnosis(updatedVisitDiagnosisObs.getUuid(), updatedVisitDiagnosisMembers);
        assertDiagnosisRevised(8, updatedVisitDiagnosisMembers);
    }

    private void assertDiagnosisRevised(int diagnosisRevisedConceptId, Set<Obs> visitDiagnosisMembers) {
        Obs diagnosisRevisedObs = getMemberObsByConceptName(visitDiagnosisMembers, MRS_CONCEPT_NAME_DIAGNOSIS_REVISED);
        assertThat(diagnosisRevisedObs.getValueCoded().getId(), is(diagnosisRevisedConceptId));
    }

    private void assertInitialDiagnosis(String expectedInitialDiagnosisValue, Set<Obs> visitDiagnosisMembers) {
        Obs initialDiagnosisObs = getMemberObsByConceptName(visitDiagnosisMembers, MRS_CONCEPT_NAME_INITIAL_DIAGNOSIS);
        assertEquals(expectedInitialDiagnosisValue, initialDiagnosisObs.getValueText());
    }

    private void assertDiagnosisCertainty(int certaintyConceptId, Set<Obs> visitDiagnosisMembers) {
        Obs diagnosisCertaintyObs = getMemberObsByConceptName(visitDiagnosisMembers, MRS_CONCEPT_NAME_DIAGNOSIS_CERTAINTY);
        assertThat(diagnosisCertaintyObs.getValueCoded().getId(), is(certaintyConceptId));
    }

    private void assertDiagnosisOrder(int diagnosisOrderConceptId, Set<Obs> visitDiagnosisMembers) {
        Obs diagnosisOrderObs = getMemberObsByConceptName(visitDiagnosisMembers, MRS_CONCEPT_NAME_DIAGNOSIS_ORDER);
        assertThat(diagnosisOrderObs.getValueCoded().getId(), is(diagnosisOrderConceptId));
    }

    private void assertCodedDiagnosis(int codedDiagnosisConceptId, Set<Obs> visitDiagnosisMembers) {
        Obs codedDiagnosisObs = getMemberObsByConceptName(visitDiagnosisMembers, MRS_CONCEPT_NAME_CODED_DIAGNOSIS);
        assertThat(codedDiagnosisObs.getValueCoded().getId(), is(codedDiagnosisConceptId));
    }

    private void assertVisitDiagnosis(Obs visitDiagnosisObs, String healthId, String shrEncounterId, String conditionUuid, String comments) {
        assertEquals(MRS_CONCEPT_NAME_VISIT_DIAGNOSES, visitDiagnosisObs.getConcept().getName().getName());
        assertEquals(5, visitDiagnosisObs.getGroupMembers().size());
        assertEquals(comments, visitDiagnosisObs.getComment());

        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, conditionUuid);
        IdMapping diagnosisIdMapping = idMappingRepository.findByExternalId(externalId, IdMappingType.DIAGNOSIS);
        assertNotNull(diagnosisIdMapping);
        assertEquals(visitDiagnosisObs.getUuid(), diagnosisIdMapping.getInternalId());
        String expectedUri = "http://shr.com/patients/" + healthId + "/encounters/" + shrEncounterId + "#Condition/" + conditionUuid;
        assertEquals(expectedUri, diagnosisIdMapping.getUri());

    }

    private EmrEncounter mapDiagnosis(String filePath) throws Exception {
        Encounter encounter = new Encounter();
        encounter.setEncounterDatetime(new Date());
        return mapDiagnosis(filePath, encounter, "HID-123", "SHR_ENC_ID");
    }

    private EmrEncounter mapDiagnosis(String filePath, Encounter encounter, String healthId, String shrEncounterId) throws Exception {
        Bundle bundle = loadSampleFHIREncounter(filePath);
        Condition diagnosisCondition = getCondition(bundle);
        ShrEncounterBundle shrEncounterBundle = new ShrEncounterBundle(bundle, healthId, shrEncounterId);
        EmrEncounter emrEncounter = new EmrEncounter(encounter);
        diagnosisConditionMapper.map(diagnosisCondition, emrEncounter, shrEncounterBundle, getSystemProperties("1"));
        return emrEncounter;
    }

    private Obs getMemberObsByConceptName(Set<Obs> visitDiagnosisMembers, String conceptName) {
        for (Obs visitDiagnosisMember : visitDiagnosisMembers) {
            if (conceptName.equals(visitDiagnosisMember.getConcept().getName().getName()))
                return visitDiagnosisMember;
        }
        return null;
    }

    private Condition getCondition(Bundle bundle) throws Exception {
        IResource resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Condition().getResourceName());
        return (Condition) resource;
    }

    private Bundle loadSampleFHIREncounter(String filePath) throws Exception {
        return (Bundle) new MapperTestHelper().loadSampleFHIREncounter(filePath, springContext);
    }
}