package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.module.fhir.utils.Constants;
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
        Encounter encounter = new Encounter();
        encounter.setEncounterType(new EncounterType("foo", "bar"));
        encounter.setPatient(getPatient("1234"));
        encounter.setVisit(getVisit());

        ca.uhn.fhir.model.dstu2.resource.Encounter fhirEncounter = encounterMapper.map(encounter, getSystemProperties("1"));
        ResourceReferenceDt subject = fhirEncounter.getPatient();
        assertEquals("1234", subject.getDisplay().getValue());
        assertEquals("http://public.com/api/default/patients/1234", subject.getReference().getValue());
    }

    private Visit getVisit() {
        Visit visit = new Visit(2000);
        visit.setVisitType(new VisitType("foo", "bar"));
        return visit;
    }

    private Patient getPatient(String healthId) {
        Patient patient = new Patient(1000);
        PersonAttribute healthIdAttribute = getHealthIdAttribute(healthId);
        patient.addAttribute(healthIdAttribute);
        return patient;
    }

    private PersonAttribute getHealthIdAttribute(String healthId) {
        PersonAttribute healthIdAttribute = new PersonAttribute();
        PersonAttributeType healthAttributeType = new PersonAttributeType();
        healthAttributeType.setName(Constants.HEALTH_ID_ATTRIBUTE);
        healthIdAttribute.setAttributeType(healthAttributeType);
        healthIdAttribute.setValue(healthId);
        return healthIdAttribute;
    }
}