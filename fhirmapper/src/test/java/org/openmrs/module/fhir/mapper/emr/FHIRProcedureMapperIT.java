package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Procedure;
import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.ObsHelper;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRProcedureMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private FHIRProcedureMapper fhirProcedureMapper;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private ApplicationContext springContext;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private IResource resource;
    private Bundle bundle;
    private ObsHelper obsHelper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/procedureDS.xml");
        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedure.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Procedure().getResourceName());
        obsHelper = new ObsHelper();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleResourceTypeOfProcedure() {
        assertTrue(fhirProcedureMapper.canHandle(resource));
    }

    @Test
    public void shouldMapStartDate() throws Exception {
        Obs proceduresObs = mapProceduresObs();
        Obs startDate = obsHelper.findMemberObsByConceptName(proceduresObs, MRS_CONCEPT_PROCEDURE_START_DATE);
        DateTime dateTime = new DateTime(DateTimeZone.forTimeZone(TimeZone.getTimeZone("IST")));
        Date expectedDate = dateTime.withDate(2015, 8, 4).toDateMidnight().toDate();
        assertEquals(expectedDate, startDate.getValueDatetime());
    }

    @Test
    public void shouldMapEndDate() throws Exception {
        Obs proceduresObs = mapProceduresObs();
        Obs endDate = obsHelper.findMemberObsByConceptName(proceduresObs, MRS_CONCEPT_PROCEDURE_END_DATE);
        DateTime dateTime = new DateTime(DateTimeZone.forTimeZone(TimeZone.getTimeZone("IST")));
        Date expectedDate = dateTime.withDate(2015, 8, 20).toDateMidnight().toDate();
        assertEquals(expectedDate, endDate.getValueDatetime());

    }

    @Test
    public void shouldMapOutCome() throws Exception {
        Obs proceduresObs = mapProceduresObs();
        Obs outcomeObs = obsHelper.findMemberObsByConceptName(proceduresObs, TrValueSetType.PROCEDURE_OUTCOME.getDefaultConceptName());
        int outcomeType = 606;
        assertEquals(conceptService.getConcept(outcomeType), outcomeObs.getValueCoded());
    }

    @Test
    public void shouldMapFollowUp() throws Exception {
        Obs proceduresObs = mapProceduresObs();
        Obs followUpObs = obsHelper.findMemberObsByConceptName(proceduresObs, MRS_CONCEPT_PROCEDURE_FOLLOWUP);
        assertEquals(conceptService.getConcept(607), followUpObs.getValueCoded());
    }

    @Test
    public void shouldMapProcedureNotes() throws Exception {
        Obs proceduresObs = mapProceduresObs();
        Obs notesObs = obsHelper.findMemberObsByConceptName(proceduresObs, MRSProperties.MRS_CONCEPT_PROCEDURE_NOTES);
        assertEquals("Procedure went well", notesObs.getValueText());
    }

    @Test
    public void shouldMapProcedureType() throws Exception {
        Obs procedureObs = mapProceduresObs();
        Obs procedureTypeObs = obsHelper.findMemberObsByConceptName(procedureObs, MRS_CONCEPT_PROCEDURE_TYPE);
        Concept valueCoded = procedureTypeObs.getValueCoded();
        int procedureType = 601;
        assertEquals(conceptService.getConcept(procedureType), valueCoded);
    }

    @Test
    public void shouldMapProcedureStatus() throws Exception {
        Obs procedureObs = mapProceduresObs();
        Obs procedureStatusObs = obsHelper.findMemberObsByConceptName(procedureObs, TrValueSetType.PROCEDURE_STATUS.getDefaultConceptName());
        Concept valueCoded = procedureStatusObs.getValueCoded();
        int procedureStatusConceptId = 604;
        assertEquals(conceptService.getConcept(procedureStatusConceptId), valueCoded);
    }

    @Test
    public void shouldMapDiagnosticReport() throws Exception {
        Obs procedureObs = mapProceduresObs();
        Obs diagnosticStudyObs = obsHelper.findMemberObsByConceptName(procedureObs, MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_STUDY);

        Obs diagnosticTestName = obsHelper.findMemberObsByConceptName(diagnosticStudyObs, MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_TEST);
        Concept diagnosticTestCode = diagnosticTestName.getValueCoded();
        int testAConceptId = 602;
        assertEquals(conceptService.getConcept(testAConceptId), diagnosticTestCode);

        Obs result = obsHelper.findMemberObsByConceptName(diagnosticStudyObs, MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT);
        assertEquals("positive", result.getValueText());

        Obs diagnosis = obsHelper.findMemberObsByConceptName(diagnosticStudyObs, MRS_CONCEPT_PROCEDURE_DIAGNOSIS);
        Concept diagnosisConcept = diagnosis.getValueCoded();
        int diagnosisConceptId = 603;
        assertEquals(diagnosisConcept, conceptService.getConcept(diagnosisConceptId));
    }

    @Test
    public void shouldCreateProcedureTypeConceptIfNotPresentLocally() throws Exception {
        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedureTypeNotPresentLocally.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Procedure().getResourceName());

        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        fhirProcedureMapper.map(resource, emrEncounter, new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-2"), getSystemProperties("1"));
        assertEquals(1, emrEncounter.getObs().size());
        Obs procedureTemplate = emrEncounter.getObs().iterator().next();
        CompoundObservation compoundObservationTemplate = new CompoundObservation(procedureTemplate);
        String fullName = "ProcedureLocal" + UNVERIFIED_BY_TR;
        Concept createdConcept = conceptService.getConceptByName(fullName);

        Obs obs = compoundObservationTemplate.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_TYPE);
        assertEquals(createdConcept, obs.getValueCoded());
        String version = String.format("%s%s", LOCAL_CONCEPT_VERSION_PREFIX, "10019842");
        assertEquals(version, createdConcept.getVersion());
        assertEquals(conceptService.getConceptClassByUuid(ConceptClass.PROCEDURE_UUID), createdConcept.getConceptClass());
    }

    @Test
    public void shouldMapAProcedureWithRequestAsProcedureFulfillment() throws Exception {
        Integer orderId = 150;
        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedureFulfillment.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Procedure().getResourceName());

        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        fhirProcedureMapper.map(resource, emrEncounter, new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-2"), getSystemProperties("1"));

        Set<Obs> topLevelObs = emrEncounter.getObs();
        assertEquals(1, topLevelObs.size());
        Obs fulfillmentObs = topLevelObs.iterator().next();

        assertEquals(MRS_CONCEPT_PROCEDURE_ORDER_FULFILLMENT_FORM, fulfillmentObs.getConcept().getName().getName());
        Obs procedureTemplateObs = fulfillmentObs.getGroupMembers().iterator().next();
        assertEquals(MRS_CONCEPT_PROCEDURES_TEMPLATE, procedureTemplateObs.getConcept().getName().getName());
        assertEquals(orderId, fulfillmentObs.getOrder().getId());
        assertEquals(orderId, procedureTemplateObs.getOrder().getId());
        Set<Obs> members = procedureTemplateObs.getGroupMembers();
        assertMemberOrders(members, orderId);
    }

    @Test
    public void shouldFailIfProcedureConceptIsPresentButProcedureOrderIsNotPresent() throws Exception {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("The procedure order with SHR reference [http://172.18.46.156:8081/patients/HID123/encounters/shr-enc-id-1#ProcedureRequest/invalid-procedure-req-id] is not yet synced");

        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedureReferringToNotPresentProcedureOrder.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Procedure().getResourceName());

        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        fhirProcedureMapper.map(resource, emrEncounter, new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-2"), getSystemProperties("1"));
    }

    @Test
    public void shouldThrowAnErrorIfProcedureTypeConceptIsNotSyncedFromTR() throws Exception {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Can not create observation, concept ProcedureAnswer1 not yet synced");

        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedureTypeNotSyncedFromTr.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Procedure().getResourceName());

        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        fhirProcedureMapper.map(resource, emrEncounter, new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-2"), getSystemProperties("1"));
    }

    private void assertMemberOrders(Set<Obs> members, Integer orderId) {
        for (Obs member : members) {
            Set<Obs> groupMembers = member.getGroupMembers();
            if (CollectionUtils.isEmpty(groupMembers))
                assertEquals(orderId, member.getOrder().getId());
            else
                assertMemberOrders(groupMembers, orderId);
        }
    }

    private Obs mapProceduresObs() {
        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        fhirProcedureMapper.map(resource, emrEncounter, new ShrEncounterBundle(bundle, "98101039678", "shr-enc-id-1"), getSystemProperties("1"));

        Set<Obs> allObs = emrEncounter.getObs();
        assertEquals(1, allObs.size());
        Obs obs = allObs.iterator().next();
        assertEquals(MRS_CONCEPT_PROCEDURES_TEMPLATE, obs.getConcept().getName().getName());

        return obs;
    }
}