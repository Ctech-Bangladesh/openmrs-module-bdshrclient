package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.ict4h.atomfeed.client.domain.Event;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.OpenMRSConstants;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.mapper.PatientMapper;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.PhoneNumber;
import org.openmrs.module.shrclient.model.Relation;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;
import static org.openmrs.module.shrclient.util.Headers.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PatientPushIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private PatientService patientService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private PersonService personService;
    @Autowired
    private PropertiesReader propertiesReader;
    @Autowired
    private IdMappingRepository idMappingsRepository;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private IdentityStore identityStore;
    @Autowired
    private LocationService locationService;

    private PatientPush patientPush;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9997);
    private final String clientIdValue = "12345";
    private final String email = "email@gmail.com";
    private final String accessToken = UUID.randomUUID().toString();

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/attributeTypesDS.xml");
        String response = "{\"access_token\" : \"" + accessToken + "\"}";
        String xAuthToken = "xyz";
        givenThat(post(urlEqualTo("/signin"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(xAuthToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(response)));


        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);
        PatientMapper patientMapper = new PatientMapper(new BbsCodeService(), idMappingsRepository);
        patientPush = new PatientPush(patientService, systemUserService, personService,
                patientMapper, propertiesReader, clientRegistry,
                idMappingsRepository, providerService, locationService);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldUploadANewPatient() throws Exception {
        executeDataSet("testDataSets/attributeDS.xml");
        Date date = DateUtil.parseDate("1992-12-24 20:03:00");
        PhoneNumber expected = new PhoneNumber();
        expected.setNumber("Phone Number");

        String mciResponse = "{\"http_status\" : \"" + 201 + "\", \"id\" : \"hid-1\"}";
        givenThat(post(urlEqualTo("/api/default/patients"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(mciResponse)));


        String patientUuid = "75e04d42-3ca8-11e3-bf2b-0800271c1b75";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/patient/" + patientUuid + "?v=full");

        patientPush.process(event);

        verify(1, postRequestedFor(urlEqualTo("/api/default/patients"))
                        .withHeader(AUTH_TOKEN_KEY, matching(accessToken))
                        .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                        .withHeader(FROM_KEY, matching(email))
        );

        List<LoggedRequest> all = wireMockRule.findAll(postRequestedFor(urlEqualTo("/api/default/patients")));
        LoggedRequest loggedRequest = all.get(0);
        Patient pushedPatient = new ObjectMapper().readValue(loggedRequest.getBodyAsString(), Patient.class);
        assertEquals(date, pushedPatient.getDateOfBirth());
        assertEquals("nid", pushedPatient.getNationalId());
        assertEquals("brn", pushedPatient.getBirthRegNumber());
        assertEquals("House hold code", pushedPatient.getHouseHoldCode());
        assertEquals(expected, pushedPatient.getPhoneNumber());
        assertEquals("REGISTERED", pushedPatient.getHidCardStatus());
        Relation[] relations = pushedPatient.getRelations();
        assertEquals(3, relations.length);

        org.openmrs.Patient patient = patientService.getPatient(11);
        assertEquals("hid-1", patient.getPatientIdentifier(OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE_NAME).getIdentifier());
        assertEquals("hid-1", patient.getAttribute(OpenMRSConstants.HEALTH_ID_ATTRIBUTE).getValue());
    }

    @Test
    public void shouldUploadAPatientVoidedAttributes() throws Exception {
        executeDataSet("testDataSets/attributeVoidedDS.xml");
        Date date = DateUtil.parseDate("1992-12-24 20:03:00");

        String mciResponse = "{\"http_status\" : \"" + 201 + "\", \"id\" : \"hid-1\"}";
        givenThat(post(urlEqualTo("/api/default/patients"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(mciResponse)));


        String patientUuid = "75e04d42-3ca8-11e3-bf2b-0800271c1b75";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/patient/" + patientUuid + "?v=full");

        patientPush.process(event);

        verify(1, postRequestedFor(urlEqualTo("/api/default/patients"))
                        .withHeader(AUTH_TOKEN_KEY, matching(accessToken))
                        .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                        .withHeader(FROM_KEY, matching(email))
        );

        List<LoggedRequest> all = wireMockRule.findAll(postRequestedFor(urlEqualTo("/api/default/patients")));
        LoggedRequest loggedRequest = all.get(0);
        Patient pushedPatient = new ObjectMapper().readValue(loggedRequest.getBodyAsString(), Patient.class);
        assertEquals("", pushedPatient.getNationalId());
        assertEquals("", pushedPatient.getBirthRegNumber());
        assertNull(pushedPatient.getPhoneNumber());
        assertEquals("", pushedPatient.getHouseHoldCode());
        assertEquals("", pushedPatient.getHouseHoldCode());
        Relation[] relations = pushedPatient.getRelations();
        assertEquals(3, relations.length);
        for (Relation relation : relations) {
            assertDeletedRelation(relation);
        }
        assertEquals(date, pushedPatient.getDateOfBirth());

        org.openmrs.Patient patient = patientService.getPatient(11);
        assertEquals("hid-1", patient.getPatientIdentifier(OpenMRSConstants.HEALTH_ID_IDENTIFIER_TYPE_NAME).getIdentifier());
        assertEquals("hid-1", patient.getAttribute(OpenMRSConstants.HEALTH_ID_ATTRIBUTE).getValue());

    }

    private void assertDeletedRelation(Relation relation) {
        assertNotNull(relation.getType());
        assertNull(relation.getGivenName());
        assertNull(relation.getSurName());
        assertNull(relation.getHid());
        assertNull(relation.getNid());
        assertNotNull(relation.getId());
    }
}