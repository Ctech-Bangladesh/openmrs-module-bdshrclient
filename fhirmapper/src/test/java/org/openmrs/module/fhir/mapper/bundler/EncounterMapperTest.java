package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.OMRSLocationService;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

public class EncounterMapperTest {

    @Mock
    private OMRSLocationService omrsLocationService;

    @InjectMocks
    private EncounterMapper encounterMapper;

    @Before
    public void setUp(){
        initMocks(this);
    }

    @Test
    public void shouldSetSubject() throws Exception {
        String healthId = "1234";
        Encounter encounter = getMrsEncounter("foo", "foo");
        FHIREncounter fhirEncounter = encounterMapper.map(encounter, healthId, getSystemProperties("1"));

        Reference subject = fhirEncounter.getPatient();
        assertEquals(healthId, subject.getDisplay());
        assertEquals("http://public.com/api/default/patients/" + healthId, subject.getReference());
    }

    @Test
    public void shouldSetVisitType() throws Exception {
        mapEncounterWithVisitType("LAB VISIT");
//        assertEquals(EncounterClassEnum.OUTPATIENT.getCode(), mapEncounterWithVisitType("LAB VISIT").getClassElement());
//        assertEquals(EncounterClassEnum.HOME.getCode(), mapEncounterWithVisitType("home").getClassElement());
//        assertEquals(EncounterClassEnum.FIELD.getCode(), mapEncounterWithVisitType("field").getClassElement());
//        assertEquals(EncounterClassEnum.AMBULATORY.getCode(), mapEncounterWithVisitType("ambulatory").getClassElement());
//        assertEquals(EncounterClassEnum.EMERGENCY.getCode(), mapEncounterWithVisitType("emergency").getClassElement());
//        assertEquals(EncounterClassEnum.OUTPATIENT.getCode(), mapEncounterWithVisitType("outpatient").getClassElement());
//        assertEquals(EncounterClassEnum.INPATIENT.getCode(), mapEncounterWithVisitType("inpatient").getClassElement());
//        assertEquals(EncounterClassEnum.OUTPATIENT.getCode(), mapEncounterWithVisitType("OPD").getClassElement());
//        assertEquals(EncounterClassEnum.INPATIENT.getCode(), mapEncounterWithVisitType("IPD").getClassElement());
    }

    private org.hl7.fhir.dstu3.model.Encounter mapEncounterWithVisitType(String visitType) {
        String healthId = "1234";
        Encounter encounter = getMrsEncounter("foo", visitType);
        return encounterMapper.map(encounter, healthId, getSystemProperties("1")).getEncounter();
    }

    private Encounter getMrsEncounter(String encounterType, String visitType) {
        Encounter encounter = new Encounter();
        encounter.setEncounterType(new EncounterType(encounterType, "Desc"));
        Patient patient = new Patient(1000);
        encounter.setPatient(patient);
        Visit visit = new Visit(3000);
        visit.setVisitType(new VisitType(visitType, "Desc"));
        encounter.setVisit(visit);
        return encounter;
    }
}