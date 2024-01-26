package org.openmrs.module.shrclient.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.service.impl.EMRPatientDeathServiceImpl;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.MRSProperties.*;

public class EMRPatientDeathServiceTest {
    @Mock
    private GlobalPropertyLookUpService mockGlobalPropertyLookUpService;
    @Mock
    private ConceptService mockConceptService;

    private EMRPatientDeathService patientDeathService;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        patientDeathService = new EMRPatientDeathServiceImpl(null, mockConceptService, mockGlobalPropertyLookUpService);
    }

    @Test
    public void shouldThrowRunTimeExceptionIfUnspecifiedCauseOfDeathConceptNotConfiguredInGlobalSettings() throws Exception {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage(String.format("Global Property %s is not set & Concept with name %s is not found", GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH, TR_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH));

        Concept causeOfDeathConcept = new Concept(1001);
        when(mockGlobalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH)).thenReturn("1001");
        when(mockConceptService.getConcept(causeOfDeathConcept.getId())).thenReturn(causeOfDeathConcept);
        when(mockGlobalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH)).thenReturn(null);

        Patient patient = new Patient();
        patient.setDead(true);

        patientDeathService.getCauseOfDeath(patient);
    }

    @Test
    public void shouldThrowRunTimeExceptionIfCauseOfDeathConceptNotConfiguredInGlobalSettings() throws Exception {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage(String.format("Global Property %s is not set & Concept with name %s is not found", GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH, TR_CONCEPT_CAUSE_OF_DEATH));

        Concept unspecifiedCauseOfDeathConcept = new Concept(1002);
        when(mockGlobalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH)).thenReturn(null);
        when(mockGlobalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH)).thenReturn("1002");
        when(mockConceptService.getConcept(unspecifiedCauseOfDeathConcept.getId())).thenReturn(unspecifiedCauseOfDeathConcept);

        Patient patient = new Patient();
        patient.setDead(true);
        patientDeathService.getCauseOfDeath(patient);
    }
}