package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Immunization;
import org.hl7.fhir.dstu3.model.Resource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.ObsHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRImmunizationMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIRImmunizationMapper mapper;

    @Autowired
    private ConceptService conceptService;

    private Resource resource;
    private Bundle bundle;
    private ObsHelper obsHelper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/immunizationDS.xml");
        bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithImmunization.xml", springContext);
        resource = FHIRBundleHelper.identifyFirstResourceWithName(bundle, new Immunization().getResourceType().name());
        obsHelper = new ObsHelper();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleResourceOfTypeImmnunization() throws Exception {
        assertTrue(mapper.canHandle(resource));
    }

    @Test
    public void shouldMapDateOfVaccination() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        DateTime dateTime = new DateTime(DateTimeZone.forTimeZone(TimeZone.getTimeZone("IST")));
        Date expectedDate = dateTime.withDate(2015, 8, 17).toDateMidnight().toDate();

        Obs vaccineDateObs = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_VACCINATION_DATE);
        assertEquals(expectedDate, vaccineDateObs.getValueDate());
    }

    @Test
    public void shouldMapVaccinationReported() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);

        Obs vaccinationReported = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_VACCINATION_REPORTED);
        assertTrue(vaccinationReported.getValueAsBoolean());
    }

    @Test
    public void shouldMapImmunizationStatus() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);

        Obs vaccinationStatus = obsHelper.findMemberObsByConceptName(immunizationGroupObs, TR_VALUESET_IMMUNIZATION_STATUS);
        assertEquals(conceptService.getConcept(604), vaccinationStatus.getValueCoded());
    }

    @Test
    public void shouldMapRefusedIndicator() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs refusedIndicator = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_VACCINATION_REFUSED);
        assertFalse(refusedIndicator.getValueAsBoolean());

    }

    @Test
    public void shouldMapQuantityUnits() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs quantityUnits = obsHelper.findMemberObsByConceptName(immunizationGroupObs, TR_VALUESET_QUANTITY_UNITS);

        int mgConceptCode = 50;
        assertEquals(conceptService.getConcept(mgConceptCode), quantityUnits.getValueCoded());
    }

    @Test
    public void shouldMapDosage() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs dosage = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_DOSAGE);

        assertTrue(dosage.getValueNumeric() == 100.0);
    }

    @Test
    public void shouldMapVaccineType() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs vaccineType = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_VACCINE);

        assertEquals(conceptService.getConcept(500), vaccineType.getValueCoded());
        assertEquals(conceptService.getDrug(550), vaccineType.getValueDrug());
    }

    @Test
    public void shouldMapImmunizationReason() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs immunizationReason = obsHelper.findMemberObsByConceptName(immunizationGroupObs, TR_VALUESET_IMMUNIZATION_REASON);

        int travelVaccination = 501;
        assertEquals(immunizationReason.getValueCoded(), conceptService.getConcept(travelVaccination));
    }

    @Test
    public void shouldMapImmunizationRefusalReason() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs immunizationReason = obsHelper.findMemberObsByConceptName(immunizationGroupObs, TR_VALUESET_IMMUNIZATION_REFUSAL_REASON);

        int patientObjection = 502;
        assertEquals(immunizationReason.getValueCoded(), conceptService.getConcept(patientObjection));
    }

    @Test
    public void shouldMapRoute() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        assertNotNull(immunizationGroupObs);
        Obs route = obsHelper.findMemberObsByConceptName(immunizationGroupObs, TR_VALUESET_ROUTE_OF_ADMINSTRATION);

        int oral = 503;
        assertEquals(route.getValueCoded(), conceptService.getConcept(oral));
    }

    @Test
    public void shouldMapImmunizationNote() throws Exception {
        Obs obs = mapImmunizationIncidentObs();
        Obs immunizationGroupObs = obsHelper.findMemberObsByConceptName(obs, MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        Obs immunizationNoteObs = obsHelper.findMemberObsByConceptName(immunizationGroupObs, MRS_CONCEPT_IMMUNIZATION_NOTE);
        assertEquals("immunization notes", immunizationNoteObs.getValueText());
    }

    private Obs mapImmunizationIncidentObs() {
        EmrEncounter emrEncounter = new EmrEncounter(new Encounter());
        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98104750156", "shr-enc-id-1");
        mapper.map(resource, emrEncounter, encounterComposition, getSystemProperties("1"));

        Set<Obs> allObs = emrEncounter.getObs();
        assertEquals(1, allObs.size());
        Obs immunizationIncidentObs = allObs.iterator().next();
        assertEquals(MRS_CONCEPT_IMMUNIZATION_INCIDENT_TEMPLATE, immunizationIncidentObs.getConcept().getName().getName());

        return immunizationIncidentObs;
    }
}