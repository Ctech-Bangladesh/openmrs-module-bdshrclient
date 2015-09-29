package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Procedure;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.ObsHelper;
import org.openmrs.module.fhir.utils.FHIRFeedHelper;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRProcedureMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    FHIRProcedureMapper fhirProcedureMapper;
    @Autowired
    ConceptService conceptService;
    @Autowired
    private ApplicationContext springContext;

    private IResource resource;
    private Bundle bundle;
    private ObsHelper obsHelper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/procedureDS.xml");
        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/dstu2/encounterWithProcedure.xml", springContext);
        resource = FHIRFeedHelper.identifyResource(bundle.getEntry(), new Procedure().getResourceName());
        obsHelper = new ObsHelper();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }


    @Test
    public void shouldHandleResourceTypeOfProcedure() {
        fhirProcedureMapper.canHandle(resource);
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
        Obs followUpObs = obsHelper.findMemberObsByConceptName(proceduresObs, TrValueSetType.PROCEDURE_FOLLOWUP.getDefaultConceptName());
        assertEquals(conceptService.getConcept(607), followUpObs.getValueCoded());
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

    private Obs mapProceduresObs() {
        Encounter mrsEncounter = new Encounter();
        fhirProcedureMapper.map(bundle, resource, null, mrsEncounter);

        Set<Obs> allObs = mrsEncounter.getAllObs();
        assertEquals(1, allObs.size());
        Obs obs = allObs.iterator().next();
        assertEquals(MRS_CONCEPT_PROCEDURES_TEMPLATE, obs.getConcept().getName().getName());

        return obs;
    }
}