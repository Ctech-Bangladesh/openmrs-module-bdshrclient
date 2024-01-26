package org.openmrs.module.shrclient.feeds.shr;

import com.sun.syndication.feed.atom.Category;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Reference;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.service.EMREncounterService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.shrclient.web.controller.dto.EncounterEvent.ENCOUNTER_UPDATED_CATEGORY_TAG;

public class DefaultEncounterFeedWorkerTest {

    @Mock
    private EMRPatientService emrPatientService;
    @Mock
    private EMREncounterService emrEncounterService;
    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private ClientRegistry clientRegistry;
    private DefaultEncounterFeedWorker encounterFeedWorker;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        encounterFeedWorker = new DefaultEncounterFeedWorker(emrPatientService, emrEncounterService, propertiesReader, clientRegistry);
    }

    @Test
    public void shouldSyncConfidentialEncounter() throws Exception {
        EncounterEvent encounterEvent = new EncounterEvent();
        encounterEvent.setTitle("Encounter:shr-enc-id");
        Bundle bundle = new Bundle();
        String healthId = "health_id";
        String mciUrl = "http://mci.com/api/patients/";
        String patientUrl = mciUrl + healthId;
        Reference patientReference = new Reference(patientUrl);
        Composition composition = new Composition();
        composition.setSubject(patientReference);
        composition.setConfidentiality(Composition.DocumentConfidentiality.R);
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        String encounterUpdatedDate = DateUtil.toISOString(DateTime.now().toDate());
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + encounterUpdatedDate);
        encounterEvent.setCategories(asList(category));
        encounterEvent.addContent(bundle);

        when(propertiesReader.getMciPatientContext()).thenReturn(mciUrl);
        RestClient mciClient = mock(RestClient.class);
        when(clientRegistry.getMCIClient()).thenReturn(mciClient);
        Patient patient = new Patient();
        when(mciClient.get(patientUrl, Patient.class)).thenReturn(patient);
        org.openmrs.Patient openmrsPatient = new org.openmrs.Patient();
        when(emrPatientService.createOrUpdateEmrPatient(patient)).thenReturn(openmrsPatient);

        encounterFeedWorker.process(encounterEvent);

        verify(mciClient, times(1)).get(patientUrl, Patient.class);
        verify(emrEncounterService, times(1)).createOrUpdateEncounter(openmrsPatient, encounterEvent);
    }

    @Test
    public void shouldDownloadMergedWithPatientIfEncounterEventIsForRetiredPatient() throws Exception {
        EncounterEvent failedEncounterEventWithRetiredPatient = new EncounterEvent();
        failedEncounterEventWithRetiredPatient.setTitle("Encounter:shr-enc-id");
        Bundle bundle = new Bundle();
        String retiredPatientId1 = "retired_patient_id_1";
        String retiredPatientId2 = "retired_patient_id_2";
        String activePatientId = "active_patient_id";
        Reference patientReference = new Reference(String.format("http://mci.com/api/patients/%s", retiredPatientId1));
        Composition composition = new Composition();
        composition.setSubject(patientReference);
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);

        failedEncounterEventWithRetiredPatient.addContent(bundle);
        RestClient mciClient = mock(RestClient.class);
        when(clientRegistry.getMCIClient()).thenReturn(mciClient);
        when(propertiesReader.getMciPatientContext()).thenReturn("http://mci.com/api/patients");
        org.openmrs.Patient savedEMRPatient = new org.openmrs.Patient();
        when(emrPatientService.createOrUpdateEmrPatient(any(Patient.class))).thenReturn(savedEMRPatient);
        Patient retiredPatient1 = getRetiredPatient(retiredPatientId1, retiredPatientId2);
        Patient retiredPatient2 = getRetiredPatient(retiredPatientId2, activePatientId);

        Patient activePatient = new Patient();
        activePatient.setHealthId(activePatientId);
        when(mciClient.get("http://mci.com/api/patients/"+ retiredPatientId1, Patient.class)).thenReturn(retiredPatient1);
        when(mciClient.get("http://mci.com/api/patients/" + retiredPatientId2, Patient.class)).thenReturn(retiredPatient2);
        when(mciClient.get("http://mci.com/api/patients/" + activePatientId, Patient.class)).thenReturn(activePatient);

        encounterFeedWorker.process(failedEncounterEventWithRetiredPatient);

        verify(mciClient, times(1)).get("http://mci.com/api/patients/"+ retiredPatientId1,Patient.class);
        verify(mciClient, times(1)).get("http://mci.com/api/patients/"+ retiredPatientId2,Patient.class);
        verify(mciClient, times(1)).get("http://mci.com/api/patients/"+ activePatientId,Patient.class);
        ArgumentCaptor<Patient> patientArgumentCaptor = ArgumentCaptor.forClass(Patient.class);
        ArgumentCaptor<EncounterEvent> encounterEventArgumentCaptor = ArgumentCaptor.forClass(EncounterEvent.class);

        verify(emrPatientService, times(1)).createOrUpdateEmrPatient(patientArgumentCaptor.capture());
        Patient toBeMappedMCIPatient = patientArgumentCaptor.getValue();
        assertEquals("active_patient_id", toBeMappedMCIPatient.getHealthId());

        verify(emrEncounterService, times(1)).createOrUpdateEncounter(eq(savedEMRPatient), encounterEventArgumentCaptor.capture());
        EncounterEvent capturedEncounterEvent = encounterEventArgumentCaptor.getValue();
        assertEquals("active_patient_id", capturedEncounterEvent.getHealthId());


    }

    private Patient getRetiredPatient(String healthId, String mergedWith) {
        Patient retiredPatient = new Patient();
        retiredPatient.setHealthId(healthId);
        retiredPatient.setActive(false);
        retiredPatient.setMergedWith(mergedWith);
        return retiredPatient;
    }
}
