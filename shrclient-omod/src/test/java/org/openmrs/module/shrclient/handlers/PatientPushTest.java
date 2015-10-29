package org.openmrs.module.shrclient.handlers;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.ict4h.atomfeed.client.domain.Event;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.User;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.mapper.PatientMapper;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.mci.api.MciPatientUpdateResponse;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.Constants.HEALTH_ID_ATTRIBUTE;
import static org.openmrs.module.fhir.Constants.ID_MAPPING_PATIENT_TYPE;

public class PatientPushTest {

    @Mock
    private PatientService patientService;
    @Mock
    private SystemUserService systemUserService;
    @Mock
    private PersonService personService;
    @Mock
    private PatientMapper patientMapper;

    @Mock
    private PropertiesReader propertiesReader;
    @Mock
    private IdMappingsRepository idMappingsRepository;

    @Mock
    private ClientRegistry clientRegistry;
    @Mock
    private RestClient mockMciRestClient;
    @Mock
    private ProviderService providerService;

    private PatientPush patientPush;
    private String healthId = "hid-200";

    private String mciPatientContext;
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(clientRegistry.getMCIClient()).thenReturn(mockMciRestClient);
        patientPush = new PatientPush(patientService, systemUserService, personService, patientMapper,
                propertiesReader, clientRegistry, idMappingsRepository, providerService);
        mciPatientContext = "/api/default/patients";
        when(propertiesReader.getMciPatientContext()).thenReturn(mciPatientContext);
        when(propertiesReader.getMciProperties()).thenReturn(new Properties() {{
            put(PropertyKeyConstants.MCI_REFERENCE_PATH, "http://public.com/");
            put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/default/patients");
        }});
    }

    @Test
    public void shouldGetPatientUuidFromEvent() {
        final String uuid = "123abc456";
        Event event = new Event("id100", "/openmrs/ws/rest/v1/patient/" + uuid + "?v=full");
        assertEquals(uuid, patientPush.getPatientUuid(event));
    }

    @Test
    public void shouldNotUpdateOpenMrsPatient_WhenHealthIdIsBlankOrNull() {
        patientPush.updateOpenMrsPatientHealthId(new org.openmrs.Patient(), " ");
        verify(patientService, never()).savePatient(any(org.openmrs.Patient.class));
        patientPush.updateOpenMrsPatientHealthId(new org.openmrs.Patient(), null);
        verify(patientService, never()).savePatient(any(org.openmrs.Patient.class));
    }

    @Test
    public void shouldNotUpdateOpenMrsPatient_WhenHealthIdAttributeIsSameAsProvidedHealthId() {
        final org.openmrs.Patient openMrsPatient = new org.openmrs.Patient();

        PersonAttribute healthIdAttribute = createHealthIdAttribute();

        Set<PersonAttribute> openMrsPatientAttributes = new HashSet<>();
        openMrsPatientAttributes.add(healthIdAttribute);
        openMrsPatient.setAttributes(openMrsPatientAttributes);

        patientPush.updateOpenMrsPatientHealthId(openMrsPatient, healthId);
        verify(patientService, never()).savePatient(any(org.openmrs.Patient.class));
        verify(idMappingsRepository, times(1)).saveOrUpdateMapping(any(IdMapping.class));
    }

    @Test
    public void shouldUpdateOpenMrsPatient_WhenNewHealthIdIsProvided() {
        final org.openmrs.Patient openMrsPatient = new org.openmrs.Patient();
        PersonAttributeType healthIdAttributeType = new PersonAttributeType();
        healthIdAttributeType.setName(HEALTH_ID_ATTRIBUTE);
        Set<PersonAttribute> openMrsPatientAttributes = new HashSet<>();
        openMrsPatient.setAttributes(openMrsPatientAttributes);

        when(personService.getPersonAttributeTypeByName(HEALTH_ID_ATTRIBUTE)).thenReturn(healthIdAttributeType);
        patientPush.updateOpenMrsPatientHealthId(openMrsPatient, healthId);
        verify(patientService).savePatient(any(org.openmrs.Patient.class));
        verify(idMappingsRepository, times(1)).saveOrUpdateMapping(any(IdMapping.class));
    }

    @Test
    public void shouldNotProcessIfEventTimeIsBeforeLastSyncTime() throws Exception {
        String content = "/openmrs/ws/rest/v1/patient/36c82d16-6237-4495-889f-59bd9e0d8181?v=full";
        DateTime dateTime = new DateTime();
        Date eventUpdatedDate = dateTime.toDate();
        Event event = new Event("123defc456", content, "Patient", null, eventUpdatedDate);
        Patient openMrsPatient = new Patient();

        when(patientService.getPatientByUuid("36c82d16-6237-4495-889f-59bd9e0d8181")).thenReturn(openMrsPatient);

        IdMapping idMapping = new IdMapping(openMrsPatient.getUuid(), "hid123", ID_MAPPING_PATIENT_TYPE,
                "http://mci.com/patients/hid123", dateTime.plusMinutes(1).toDate());
        when(idMappingsRepository.findByInternalId(openMrsPatient.getUuid())).thenReturn(idMapping);

        patientPush.process(event);

        verify(patientMapper, never()).map(any(Patient.class), any(SystemProperties.class));
    }

    @Test
    public void shouldRequestMciToCreateAPatient() throws Exception {
        String content = "/openmrs/ws/rest/v1/patient/36c82d16-6237-4495-889f-59bd9e0d8181?v=full";
        Date eventUpdatedDate = new Date();
        Event event = new Event("123defc456", content, "Patient", null, eventUpdatedDate);

        Patient openMrsPatient = new Patient();
        Person creator = new Person();
        openMrsPatient.setCreator(new User(creator));
        MciPatientUpdateResponse updateResponse = new MciPatientUpdateResponse();
        updateResponse.setHealthId("h100");

        org.openmrs.module.shrclient.model.Patient patient = new org.openmrs.module.shrclient.model.Patient();

        when(patientService.getPatientByUuid("36c82d16-6237-4495-889f-59bd9e0d8181")).thenReturn(openMrsPatient);
        when(patientMapper.map(any(Patient.class), any(SystemProperties.class))).thenReturn(patient);
        when(providerService.getProvidersByPerson(creator)).thenReturn(Collections.EMPTY_LIST);
        when(mockMciRestClient.post(propertiesReader.getMciPatientContext(), patient, MciPatientUpdateResponse.class))
                .thenReturn(updateResponse);

        patientPush.process(event);

        verify(mockMciRestClient, times(1))
                .post(propertiesReader.getMciPatientContext(), patient, MciPatientUpdateResponse.class);
        verify(idMappingsRepository, times(1)).saveOrUpdateMapping(any(IdMapping.class));
    }

    private PersonAttribute createHealthIdAttribute() {
        PersonAttributeType healthIdAttributeType = new PersonAttributeType();
        healthIdAttributeType.setName(HEALTH_ID_ATTRIBUTE);

        PersonAttribute healthIdAttribute = new PersonAttribute();
        healthIdAttribute.setAttributeType(healthIdAttributeType);
        healthIdAttribute.setValue(healthId);
        return healthIdAttribute;
    }
}
