package org.openmrs.module.shrclient.service;

import com.sun.syndication.feed.atom.Category;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderService;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.mapper.emr.FHIRMapper;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.advice.SHREncounterEventService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.EncounterIdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.service.impl.EMREncounterServiceImpl;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

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
import static org.openmrs.module.shrclient.web.controller.dto.EncounterEvent.ENCOUNTER_UPDATED_CATEGORY_TAG;
import static org.openmrs.module.shrclient.web.controller.dto.EncounterEvent.LATEST_UPDATE_CATEGORY_TAG;

public class EMREncounterServiceImplTest {
    @Mock
    private FHIRMapper mockFhirmapper;
    @Mock
    private IdMappingRepository mockIdMappingRepository;
    @Mock
    private VisitService mockVisitService;
    @Mock
    private SystemUserService mockSystemUserService;
    @Mock
    private PropertiesReader mockPropertiesReader;
    @Mock
    private EMRPatientDeathService patientDeathService;
    @Mock
    private EMRPatientMergeService emrPatientMergeService;
    @Mock
    private OrderService mockOrderService;
    @Mock
    private EMRPatientService mockEMRPatientService;
    @Mock
    private VisitLookupService mockVisitLookupService;
    @Mock
    private SHREncounterEventService mockShrEncounterEventService;
    @Mock
    private EncounterService encounterService;

    private EMREncounterService emrEncounterService;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        emrEncounterService = new EMREncounterServiceImpl(mockEMRPatientService, mockIdMappingRepository, mockPropertiesReader,
                mockSystemUserService, mockVisitService, mockFhirmapper, mockOrderService, patientDeathService, emrPatientMergeService, mockVisitLookupService, mockShrEncounterEventService, encounterService);
    }

    @Test
    public void shouldNotSyncConfidentialEncounter() throws Exception {
        EncounterEvent encounterEvent = new EncounterEvent();
        encounterEvent.setTitle("Encounter:shr-enc-id");
        Bundle bundle = new Bundle();
        String healthId = "health_id";
        Composition composition = getComposition(healthId);
        composition.setConfidentiality(Composition.DocumentConfidentiality.R);
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        String encounterUpdatedDate = DateUtil.toISOString(DateTime.now().toDate());
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + encounterUpdatedDate);
        encounterEvent.setCategories(asList(category));
        encounterEvent.addContent(bundle);
        Patient emrPatient = new Patient();
        String shrEncounterId = "shr-enc-id";

        when(mockIdMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER)).thenReturn(null);
        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);
        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
    }

    @Test
    public void shouldSyncNonConfidentialEncounter() throws Exception {
        EncounterEvent encounterEvent = new EncounterEvent();
        Bundle bundle = new Bundle();
        String healthId = "health_id";
        Composition composition = getComposition(healthId);
        composition.setConfidentiality(Composition.DocumentConfidentiality.N);
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterEvent.addContent(bundle);
        String encounterUpdateDate = DateUtil.toISOString(DateTime.now().toDate());
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + encounterUpdateDate);
        encounterEvent.setCategories(asList(category));

        encounterEvent.setTitle("Encounter:shr_encounter_id");
        Patient emrPatient = new Patient();
        String shrEncounterId = "shr-enc-id";

        when(mockIdMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER)).thenReturn(null);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);
        when(mockFhirmapper.getVisitPeriod(any(ShrEncounterBundle.class))).thenReturn(new Period());
        when(mockVisitLookupService.findOrInitializeVisit(eq(emrPatient), any(Date.class), any(VisitType.class), any(Location.class), any(Date.class), any(Date.class))).thenReturn(new Visit());
        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);

        verify(mockFhirmapper, times(1)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSyncAnEncounterWithUpdateTag() throws Exception {
        EncounterEvent encounterEvent = new EncounterEvent();
        Bundle bundle = new Bundle();
        String healthId = "health_id";
        Composition composition = getComposition(healthId);
        composition.setConfidentiality(Composition.DocumentConfidentiality.N);
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(composition);
        bundle.addEntry(atomEntry);
        encounterEvent.addContent(bundle);
        Category category = new Category();
        category.setTerm(LATEST_UPDATE_CATEGORY_TAG + ":event_id1");
        encounterEvent.setCategories(asList(category));
        encounterEvent.setTitle("Encounter:shr_encounter_id-1");
        Patient emrPatient = new Patient();

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);

        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSyncAnEncounterIfAlreadySynced() throws Exception {
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        atomEntry.setResource(getComposition("health_id"));
        Bundle bundle = new Bundle();
        bundle.addEntry(atomEntry);

        Calendar calendar = Calendar.getInstance();
        Date currentTime = DateTime.now().toDate();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, 2);
        Date twoMinutesAfter = calendar.getTime();

        String healthId = "health_id";
        String shrEncounterId = "shr_encounter_id";
        EncounterEvent encounterEvent = getEncounterEvent(bundle, shrEncounterId);
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + DateUtil.toISOString(currentTime));
        encounterEvent.setCategories(asList(category));
        Patient emrPatient = new Patient();

        String uri = String.format("http://shr.com/patients/%s/encounters/shr_encounter_id", healthId);
        EncounterIdMapping mapping = new EncounterIdMapping(UUID.randomUUID().toString(), shrEncounterId, uri, new Date(), twoMinutesAfter, twoMinutesAfter);
        when(mockIdMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER)).thenReturn(mapping);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);
        verify(mockFhirmapper, times(0)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
    }

    @Test
    public void shouldSyncAnEncounterIfUpdatedLater() throws Exception {
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        String healthId = "health_id";
        atomEntry.setResource(getComposition(healthId));
        Bundle bundle = new Bundle();
        bundle.addEntry(atomEntry);

        Calendar calendar = Calendar.getInstance();
        Date currentTime = DateTime.now().toDate();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, 2);
        Date twoMinutesAfter = calendar.getTime();

        String shrEncounterId = "shr_encounter_id";
        EncounterEvent encounterEvent = getEncounterEvent(bundle, shrEncounterId);
        String twoMinutesAfterDateString = DateUtil.toISOString(twoMinutesAfter);
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + twoMinutesAfterDateString);
        encounterEvent.setCategories(asList(category));
        Patient emrPatient = new Patient();

        String uri = String.format("http://shr.com/patients/%s/encounters/shr_encounter_id", healthId);
        EncounterIdMapping mapping = new EncounterIdMapping(UUID.randomUUID().toString(), shrEncounterId, uri, new Date(), currentTime, currentTime);
        when(mockIdMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER)).thenReturn(mapping);
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class))).thenReturn(new Encounter());
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);
        when(mockFhirmapper.getVisitPeriod(any(ShrEncounterBundle.class))).thenReturn(new Period());
        when(mockVisitLookupService.findOrInitializeVisit(eq(emrPatient), any(Date.class), any(VisitType.class), any(Location.class), any(Date.class), any(Date.class))).thenReturn(new Visit());

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);
        verify(mockFhirmapper, times(1)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
    }

    @Test
    public void shouldNotSaveOrderIfAlreadySaved() throws Exception {
        Bundle.BundleEntryComponent atomEntry = new Bundle.BundleEntryComponent();
        String healthId = "health_id";
        atomEntry.setResource(getComposition(healthId));
        Bundle bundle = new Bundle();
        bundle.addEntry(atomEntry);

        Calendar calendar = Calendar.getInstance();
        Date currentTime = DateTime.now().toDate();
        calendar.setTime(currentTime);
        calendar.add(Calendar.MINUTE, 2);
        Date twoMinutesAfter = calendar.getTime();

        String shrEncounterId = "shr_encounter_id";
        EncounterEvent encounterEvent = getEncounterEvent(bundle, shrEncounterId);
        String twoMinutesAfterDateString = DateUtil.toISOString(twoMinutesAfter);
        Category category = new Category();
        category.setTerm(ENCOUNTER_UPDATED_CATEGORY_TAG + ":" + twoMinutesAfterDateString);
        encounterEvent.setCategories(asList(category));
        Patient emrPatient = new Patient();

        String uri = String.format("http://shr.com/patients/%s/encounters/shr_encounter_id", healthId);
        EncounterIdMapping mapping = new EncounterIdMapping(UUID.randomUUID().toString(), shrEncounterId, uri, new Date(), currentTime, currentTime);
        when(mockIdMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER)).thenReturn(mapping);
        Encounter encounter = new Encounter();
        encounter.addOrder(new Order(1));
        when(mockFhirmapper.map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class))).thenReturn(encounter);
        when(mockPropertiesReader.getShrBaseUrl()).thenReturn("http://shr.com/");
        Properties shrProperties = new Properties();
        shrProperties.put(SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        when(mockPropertiesReader.getShrProperties()).thenReturn(shrProperties);
        when(mockFhirmapper.getVisitPeriod(any(ShrEncounterBundle.class))).thenReturn(new Period());
        when(mockVisitLookupService.findOrInitializeVisit(eq(emrPatient), any(Date.class), any(VisitType.class), any(Location.class), any(Date.class), any(Date.class))).thenReturn(new Visit());

        emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);
        verify(mockFhirmapper, times(1)).map(eq(emrPatient), any(ShrEncounterBundle.class), any(SystemProperties.class));
        verify(mockOrderService, times(0)).saveRetrospectiveOrder(any(Order.class), isNull(OrderContext.class));
    }

    private Composition getComposition(String healthId) {
        Composition composition = new Composition();
        composition.setSubject(new Reference("http://mci.com/api/default/patients/" + healthId));
        return composition;
    }

    private EncounterEvent getEncounterEvent(Bundle bundle, String shrEncounterId) {
        EncounterEvent encounterEvent = new EncounterEvent();
        encounterEvent.addContent(bundle);
        encounterEvent.setTitle("Encounter:" + shrEncounterId);
        return encounterEvent;
    }
}
