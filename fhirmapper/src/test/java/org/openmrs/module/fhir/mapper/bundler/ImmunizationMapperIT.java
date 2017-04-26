package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ImmunizationMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ObsService obsService;

    @Autowired
    private ImmunizationMapper mapper;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/immunizationDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleImmunizationTypeObservations() throws Exception {
        Obs observation = obsService.getObs(11);
        assertTrue(mapper.canHandle(observation));
    }

    @Test
    public void shouldMapSubjectToImmunizationAndSetIdentifier() throws Exception {
        Encounter fhirEncounter = new Encounter();
        Reference patient = new Reference();
        patient.setReference("Hid");
        fhirEncounter.setSubject(patient);

        Immunization immunization = mapImmunization(11, fhirEncounter);

        assertEquals(patient, immunization.getPatient());
        assertTrue(CollectionUtils.isNotEmpty(immunization.getIdentifier()));
    }

    @Test
    public void shouldMapImmunizationNotes() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        assertEquals(1, immunization.getNote().size());
        assertEquals("immunization notes", immunization.getNote().get(0).getText());
    }

    @Test
    public void shouldMapRefusedIndicator() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        assertTrue(immunization.getNotGiven());
    }

    @Test
    public void shouldSetRefusedIndicatorAsFalseIfNotPresent() throws Exception {
        Immunization immunization = mapImmunization(31, new Encounter());
        assertFalse(immunization.getNotGiven());
    }

    @Test
    public void shouldMapImmunizationStatus() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        assertEquals("in-progress", immunization.getStatus().getDisplay());
    }

    @Test
    public void shouldMapVaccine() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());

        Coding vaccineTypeCoding = immunization.getVaccineCode().getCoding().get(0);
        assertEquals("Paracetamol 500", vaccineTypeCoding.getDisplay());
        assertEquals("ABC", vaccineTypeCoding.getCode());
        assertEquals("http://tr.com/ABC", vaccineTypeCoding.getSystem());
    }

    @Test
    public void shouldMapVaccinationDate() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        Date vaccinationDate = immunization.getDate();
        assertEquals(DateUtil.parseDate("2014-01-02 00:00:00"), vaccinationDate);
    }

    @Test
    public void shouldMapReported() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        assertFalse(immunization.getPrimarySource());
    }

    @Test
    public void shouldDefaultReportedToFalseIfNotEntered() throws Exception {
        Immunization immunization = mapImmunization(31, new Encounter());
        assertTrue(immunization.getPrimarySource());
    }

    @Test
    public void shouldSetTheRequesterOfTheImmunization() throws Exception {
        Encounter fhirEncounter = new Encounter();
        Encounter.EncounterParticipantComponent requester = fhirEncounter.addParticipant();
        Reference doctor = new Reference().setReference("Life Saver");
        requester.setIndividual(doctor);

        Immunization immunization = mapImmunization(11, fhirEncounter);

        assertEquals(doctor, immunization.getPractitionerFirstRep().getActor());
    }

    @Test
    public void shouldSetDosageUnits() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        assertTrue(immunization.getDoseQuantity().getValue().doubleValue() == 10);
        assertEquals("mg", immunization.getDoseQuantity().getCode());
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Quantity-Units", immunization.getDoseQuantity().getSystem());
    }

    @Test
    public void shouldMapImmunizationReason() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        CodeableConcept immunizationReason = immunization.getExplanation().getReason().get(0);

        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Immunization-Reason", immunizationReason.getCoding().get(0).getSystem());
        assertEquals("Travel vaccinations", immunizationReason.getCoding().get(0).getCode());
        assertEquals("Travel vaccinations", immunizationReason.getCoding().get(0).getDisplay());
    }

    @Test
    public void shouldMapImmunizationRefusalReason() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        CodeableConcept immunizationRefusalReason = immunization.getExplanation().getReasonNotGiven().get(0);

        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/No-Immunization-Reason", immunizationRefusalReason.getCoding().get(0).getSystem());
        assertEquals("patient objection", immunizationRefusalReason.getCoding().get(0).getCode());
        assertEquals("patient objection", immunizationRefusalReason.getCoding().get(0).getDisplay());
    }

    @Test
    public void shouldMapRouteOfAdministration() throws Exception {
        Immunization immunization = mapImmunization(11, new Encounter());
        CodeableConcept route = immunization.getRoute();

        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Route-of-Administration", route.getCoding().get(0).getSystem());
        assertEquals("Oral", route.getCoding().get(0).getCode());
        assertEquals("Oral", route.getCoding().get(0).getDisplay());
    }

    private Immunization mapImmunization(int observationId, Encounter fhirEncounter) {
        Obs obs = obsService.getObs(observationId);
        List<FHIRResource> fhirResources = mapper.map(obs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));

        assertEquals(1, fhirResources.size());
        assertTrue(fhirResources.get(0).getResource() instanceof Immunization);
        assertTrue(CollectionUtils.isNotEmpty(fhirResources.get(0).getIdentifierList()));
        assertEquals("Immunization", fhirResources.get(0).getResourceName());

        return (Immunization) fhirResources.get(0).getResource();
    }
}