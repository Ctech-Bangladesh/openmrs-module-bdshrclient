package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.codesystems.V3ActCode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.OMRSLocationService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

public class EncounterMapperTest {

    @Mock
    private OMRSLocationService omrsLocationService;

    @InjectMocks
    private EncounterMapper encounterMapper;

    @Before
    public void setUp() {
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
        assertEquals(V3ActCode.HH.toCode(), mapEncounterWithVisitType("home", "foo").getClass_().getCode());
        assertEquals(V3ActCode.FLD.toCode(), mapEncounterWithVisitType("field", "foo").getClass_().getCode());
        assertEquals(V3ActCode.EMER.toCode(), mapEncounterWithVisitType("emergency", "foo").getClass_().getCode());

        assertEquals(V3ActCode.AMB.toCode(), mapEncounterWithVisitType("ambulatory", "foo").getClass_().getCode());
        assertEquals(V3ActCode.AMB.toCode(), mapEncounterWithVisitType("outpatient", "foo").getClass_().getCode());
        assertEquals(V3ActCode.AMB.toCode(), mapEncounterWithVisitType("OPD", "foo").getClass_().getCode());
        assertEquals(V3ActCode.AMB.toCode(), mapEncounterWithVisitType("LAB VISIT", "foo").getClass_().getCode());

        assertEquals(V3ActCode.IMP.toCode(), mapEncounterWithVisitType("inpatient", "foo").getClass_().getCode());
        assertEquals(V3ActCode.IMP.toCode(), mapEncounterWithVisitType("IPD", "foo").getClass_().getCode());
    }

    @Test
    public void shouldSetEnconterTypeFromMap() throws Exception {
        CodeableConcept encounterType = mapEncounterWithVisitType("foo", "Member Registration").getTypeFirstRep();
        assertNotNull(encounterType);
        Coding codingFirstRep = encounterType.getCodingFirstRep();
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Encounter-Type", codingFirstRep.getSystem());
        assertEquals("Registration", codingFirstRep.getCode());
        assertEquals("Registration", codingFirstRep.getDisplay());
    }

    @Test
    public void shouldSetEnconterTypeIfMappingnNotPresent() throws Exception {
        CodeableConcept encounterType = mapEncounterWithVisitType("foo", "Member").getTypeFirstRep();
        assertNotNull(encounterType);
        Coding codingFirstRep = encounterType.getCodingFirstRep();
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Encounter-Type", codingFirstRep.getSystem());
        assertEquals("Member", codingFirstRep.getCode());
        assertEquals("Member", codingFirstRep.getDisplay());
    }

    private org.hl7.fhir.dstu3.model.Encounter mapEncounterWithVisitType(String visitType, String encounterType) {
        String healthId = "1234";
        Encounter encounter = getMrsEncounter(encounterType, visitType);
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