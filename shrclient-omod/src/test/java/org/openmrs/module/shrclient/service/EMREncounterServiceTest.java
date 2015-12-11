package org.openmrs.module.shrclient.service;

import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import com.sun.syndication.feed.atom.Category;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.Constants;
import org.openmrs.module.fhir.mapper.emr.FHIRMapper;
import org.openmrs.module.fhir.mapper.model.ShrEncounter;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.module.shrclient.web.controller.dto.EncounterBundle;

import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.SHR_PATIENT_ENC_PATH_PATTERN;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.SHR_REFERENCE_PATH;

public class EMREncounterServiceTest {
    @Mock
    private FHIRMapper mockFhirmapper;
    @Mock
    private IdMappingsRepository mockIdMappingsRepository;
    @Mock
    private VisitService mockVisitService;
    @Mock
    private SystemUserService mockSystemUserService;
    @Mock
    private PropertiesReader mockPropertiesReader;
    @Mock
    private GlobalPropertyLookUpService mockGlobalPropertyLookUpService;
    @Mock
    private ConceptService mockConceptService;
    @Mock
    private EMRPatientDeathService patientDeathService;

    private EMREncounterService emrEncounterService;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        emrEncounterService = new EMREncounterService(null, mockIdMappingsRepository, mockPropertiesReader
                , mockSystemUserService, mockVisitService, mockFhirmapper, null, patientDeathService);
    }

    @Test
    public void shouldNotSyncAlreadyProcessedEncounter() throws Exception {
        EncounterBundle encounterBundle = new EncounterBundle();
        encounterBundle.setTitle("Encounter:shr-enc-id");
        Bundle bundle = new Bundle();
        Composition composition = new Composition();
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterBundle.addContent(bundle);
        Patient emrPatient = new Patient();

        when(mockIdMappingsRepository.findByExternalId(any(String.class))).thenReturn(new IdMapping());
        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, "health_id");

        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSyncConfidentialEncounter() throws Exception {
        EncounterBundle encounterBundle = new EncounterBundle();
        encounterBundle.setTitle("Encounter:shr-enc-id");
        Bundle bundle = new Bundle();
        Composition composition = new Composition();
        composition.setConfidentiality("R");
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterBundle.addContent(bundle);
        Patient emrPatient = new Patient();
        String healthId = "health_id";
        String shrEncounterId = "shr-enc-id";


        when(mockIdMappingsRepository.findByExternalId(shrEncounterId)).thenReturn(null);

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, healthId);

        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }

    @Test
    public void shouldSyncNonConfidentialEncounter() throws Exception {
        EncounterBundle encounterBundle = new EncounterBundle();
        Bundle bundle = new Bundle();
        Composition composition = new Composition();
        composition.setConfidentiality("N");
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterBundle.addContent(bundle);
        encounterBundle.setPublishedDate(DateUtil.toISOString(DateTime.now().toDate()));
        encounterBundle.setTitle("Encounter:shr_encounter_id");
        Patient emrPatient = new Patient();
        String healthId = "health_id";
        String shrEncounterId = "shr-enc-id";

        when(mockIdMappingsRepository.findByExternalId(shrEncounterId)).thenReturn(null);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, healthId);

        verify(mockFhirmapper, times(1)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSyncAnEncounterWithUpdateTag() throws Exception {
        EncounterBundle encounterBundle = new EncounterBundle();
        Bundle bundle = new Bundle();
        Composition composition = new Composition();
        composition.setConfidentiality("N");
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterBundle.addContent(bundle);
        Category category = new Category();
        category.setTerm(Constants.LATEST_UPDATE_CATEGORY_TAG + "event_id1");
        encounterBundle.setCategories(asList(category));
        encounterBundle.setTitle("Encounter:shr_encounter_id-1");
        Patient emrPatient = new Patient();
        String healthId = "health_id";

        hieEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, healthId);

        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSyncAnEncounterIfAlreadySynced() throws Exception {
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(new Composition());
        Bundle bundle = new Bundle();
        bundle.addEntry(atomEntry);

        Calendar calendar = Calendar.getInstance();
        Date currentTime = DateTime.now().toDate();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, 2);
        Date twoMinutesAfter = calendar.getTime();

        String shrEncounterId = "shr_encounter_id";
        EncounterBundle encounterBundle = new EncounterBundle();
        encounterBundle.addContent(bundle);
        encounterBundle.setTitle("Encounter:" + shrEncounterId);
        encounterBundle.setPublishedDate(DateUtil.toISOString(currentTime));
        Patient emrPatient = new Patient();
        String healthId = "health_id";

        String uri = "http://shr.com/patients/HID/encounters/shr_encounter_id";
        IdMapping mapping = new IdMapping(UUID.randomUUID().toString(), shrEncounterId, "ENC", uri, twoMinutesAfter);
        when(mockIdMappingsRepository.findByExternalId(shrEncounterId)).thenReturn(mapping);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);

        hieEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, healthId);
        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }
    
    @Test
    public void shouldSyncAnEncounterIfUpdatedLater() throws Exception {
        Bundle.Entry atomEntry = new Bundle.Entry();
        atomEntry.setResource(new Composition());
        Bundle bundle = new Bundle();
        bundle.addEntry(atomEntry);

        Calendar calendar = Calendar.getInstance();
        Date currentTime = DateTime.now().toDate();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, 2);
        Date twoMinutesAfter = calendar.getTime();

        String shrEncounterId = "shr_encounter_id";
        EncounterBundle encounterBundle = new EncounterBundle();
        encounterBundle.addContent(bundle);
        encounterBundle.setTitle("Encounter:" + shrEncounterId);
        encounterBundle.setPublishedDate(DateUtil.toISOString(twoMinutesAfter));
        Patient emrPatient = new Patient();
        String healthId = "health_id";

        String uri = "http://shr.com/patients/HID/encounters/shr_encounter_id";
        IdMapping mapping = new IdMapping(UUID.randomUUID().toString(), shrEncounterId, "ENC", uri, currentTime);
        when(mockIdMappingsRepository.findByExternalId(shrEncounterId)).thenReturn(mapping);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);

        hieEncounterService.createOrUpdateEncounter(emrPatient, encounterBundle, healthId);
        verify(mockFhirmapper, times(1)).map(eq(emrPatient), any(ShrEncounter.class), any(SystemProperties.class));
    }
}