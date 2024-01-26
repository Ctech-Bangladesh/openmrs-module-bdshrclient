package org.openmrs.module.shrclient.handlers;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityToken;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.SHRClient;
import org.springframework.http.HttpStatus;

import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.shrclient.util.Headers.*;

public class ClientRegistryTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private IdentityStore identityStore;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(propertiesReader.getIdentityProperties()).thenReturn(getIdpProperties(AUTH_TOKEN_KEY, CLIENT_ID_KEY));
        when(propertiesReader.getIdPSignInPath()).thenReturn("signin");
    }

    @Test
    public void testCreateMCIClient() throws Exception {
        String xAuthToken = "foobarbazboom";
        String clientIdValue = "18549";
        String email = "email@gmail.com";

        when(propertiesReader.getMciBaseUrl()).thenReturn("http://localhost:8089");
        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, email, "password"));

        UUID token = UUID.randomUUID();
        when(identityStore.getToken()).thenReturn(new IdentityToken(token.toString()));

        stubFor(get(urlEqualTo("http://localhost:8089/mci"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(token.toString()))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);

        RestClient mciClient = clientRegistry.getMCIClient();
        assertNotNull(mciClient);
        mciClient.get("/mci", String.class);
        verify(1, getRequestedFor(urlEqualTo("/mci"))
                .withHeader(AUTH_TOKEN_KEY, matching(token.toString()))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                .withHeader(FROM_KEY, matching(email)));
    }

    @Test
    public void testCreateMCIClientWhenIdentityTokenAbsent() throws Exception {
        String xAuthToken = "foobarbazboom";
        String clientIdValue = "18549";
        String email = "email@gmail.com";

        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, email, "password"));
        when(propertiesReader.getMciBaseUrl()).thenReturn("http://localhost:8089");
        when(propertiesReader.getIdPBaseUrl()).thenReturn("http://localhost:8089");

        //here the token is set to null, so ClientRegistry should get a new token
        when(identityStore.getToken()).thenReturn(null);

        UUID token = UUID.randomUUID();
        String response = "{\"access_token\" : \"" + token.toString() + "\"}";

        stubFor(post(urlMatching("/signin"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(xAuthToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(response)));

        stubFor(get(urlEqualTo("http://localhost:8089/mci"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(token.toString()))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);

        RestClient mciClient = clientRegistry.getMCIClient();
        assertNotNull(mciClient);
        mciClient.get("/mci", String.class);
        verify(1, getRequestedFor(urlEqualTo("/mci"))
                .withHeader(AUTH_TOKEN_KEY, matching(token.toString()))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                .withHeader(FROM_KEY, matching(email)));
    }

    @Test
    public void testCreateSHRClient() throws Exception {
        String xAuthToken = "foobarbazboom";
        String clientIdValue = "18549";
        String email = "email@gmail.com";

        when(propertiesReader.getShrBaseUrl()).thenReturn("http://localhost:8089");
        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, email, "password"));

        UUID token = UUID.randomUUID();
        when(identityStore.getToken()).thenReturn(new IdentityToken(token.toString()));

        stubFor(get(urlEqualTo("/patients/hid01/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(token.toString()))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n" +
                                "    <title>Patient Encounters</title>\n" +
                                "    <link rel=\"self\" type=\"application/atom+xml\" href=\"http://192.168.33" +
                                ".10:8081/patients/5926602583484399617/encounters\" />\n" +
                                "    <link rel=\"via\" type=\"application/atom+xml\" href=\"http://192.168.33" +
                                ".10:8081/patients/5926602583484399617/encounters\" />\n" +
                                "    <author>\n" +
                                "        <name>FreeSHR</name>\n" +
                                "    </author>\n" +
                                "    <id>2885d2c2-b534-4544-8958-0cef4b8fd1db</id>\n" +
                                "    <generator uri=\"https://github.com/ICT4H/atomfeed\">Atomfeed</generator>\n" +
                                "    <updated>2014-10-27T12:08:57Z</updated>\n" +
                                "</feed>")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);

        SHRClient shrClient = clientRegistry.getSHRClient();
        assertNotNull(shrClient);
        shrClient.getEncounters("/patients/hid01/encounters");
        verify(1, getRequestedFor(urlEqualTo("/patients/hid01/encounters"))
                .withHeader(AUTH_TOKEN_KEY, matching(token.toString()))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                .withHeader(FROM_KEY, matching(email)));
    }

    @Test
    public void testCreateSHRClientWhenIdentityTokenAbsent() throws Exception {
        String xAuthToken = "foobarbazboom";
        String clientIdValue = "18549";
        String email = "email@gmail.com";

        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, email, "password"));
        when(propertiesReader.getShrBaseUrl()).thenReturn("http://localhost:8089");
        when(propertiesReader.getIdPBaseUrl()).thenReturn("http://localhost:8089");

        //here the token is set to null, so ClientRegistry should get a new token
        when(identityStore.getToken()).thenReturn(null);

        UUID token = UUID.randomUUID();

        String response = "{\"access_token\" : \"" + token.toString() + "\"}";
        stubFor(post(urlMatching("/signin"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(xAuthToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(response)));

        stubFor(get(urlEqualTo("/patients/hid01/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(token.toString()))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n" +
                                "    <title>Patient Encounters</title>\n" +
                                "    <link rel=\"self\" type=\"application/atom+xml\" href=\"http://192.168.33" +
                                ".10:8081/patients/5926602583484399617/encounters\" />\n" +
                                "    <link rel=\"via\" type=\"application/atom+xml\" href=\"http://192.168.33" +
                                ".10:8081/patients/5926602583484399617/encounters\" />\n" +
                                "    <author>\n" +
                                "        <name>FreeSHR</name>\n" +
                                "    </author>\n" +
                                "    <id>2885d2c2-b534-4544-8958-0cef4b8fd1db</id>\n" +
                                "    <generator uri=\"https://github.com/ICT4H/atomfeed\">Atomfeed</generator>\n" +
                                "    <updated>2014-10-27T12:08:57Z</updated>\n" +
                                "</feed>")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);

        SHRClient shrClient = clientRegistry.getSHRClient();
        assertNotNull(shrClient);
        shrClient.getEncounters("/patients/hid01/encounters");
        verify(1, getRequestedFor(urlEqualTo("/patients/hid01/encounters"))
                .withHeader(AUTH_TOKEN_KEY, matching(token.toString()))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                .withHeader(FROM_KEY, matching(email)));
    }

    @Test
    public void testCreateFRClient() throws Exception {
        String xAuthToken = "foobarbazboom";
        String clientIdValue = "18549";

        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, "email@gmail.com", "password"));

        when(propertiesReader.getFrBaseUrl()).thenReturn("http://localhost:8089");

        stubFor(get(urlEqualTo("http://localhost:8089/fr"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(xAuthToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, null);

        RestClient frWebClient = clientRegistry.getFRClient();
        assertNotNull(frWebClient);
        frWebClient.get("/fr", String.class);
        verify(1, getRequestedFor(urlMatching("/fr"))
                .withHeader(AUTH_TOKEN_KEY, matching(xAuthToken))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue)));
    }

    @Test
    public void testCreateLRClient() throws Exception {
        String xAuthTokenKey = "X-Auth-Token";
        String xAuthToken = "foobarbazboom";
        String clientId = "client_id";
        String clientIdValue = "18549";

        when(propertiesReader.getIdentityProperties()).thenReturn(getIdpProperties(xAuthTokenKey, clientId));
        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(getFacilityInstanceProperties(xAuthToken, clientIdValue, "email@gmail.com", "password"));
        when(propertiesReader.getLrBaseUrl()).thenReturn("http://localhost:8089");

        stubFor(get(urlEqualTo("http://localhost:8089/lr"))
                .withHeader(xAuthTokenKey, equalTo(xAuthToken))
                .withHeader(clientId, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, null);

        RestClient lrWebClient = clientRegistry.getLRClient();
        assertNotNull(lrWebClient);
        lrWebClient.get("/lr", String.class);
        verify(1, getRequestedFor(urlMatching("/lr"))
                .withHeader(xAuthTokenKey, matching(xAuthToken))
                .withHeader(clientId, matching(clientIdValue)));
    }

    @Test
    public void testCreatePRClient() throws Exception {
        String xAuthTokenKey = "X-Auth-Token";
        String xAuthToken = "xyz";
        String clientId = "client_id";
        String clientIdValue = "18549";

        Properties idpProperties = getIdpProperties(xAuthTokenKey, clientId);
        Properties facilityInstanceProperties = getFacilityInstanceProperties(xAuthToken, clientIdValue, "email@gmail.com", "password");

        when(propertiesReader.getIdentityProperties()).thenReturn(idpProperties);
        when(propertiesReader.getFacilityInstanceProperties()).thenReturn(facilityInstanceProperties);
        when(propertiesReader.getPrBaseUrl()).thenReturn("http://localhost:8089");

        stubFor(get(urlEqualTo("http://localhost:8089/pr"))
                .withHeader(xAuthTokenKey, equalTo(xAuthToken))
                .withHeader(clientId, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("")));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, null);

        RestClient prWebClient = clientRegistry.getPRClient();
        assertNotNull(prWebClient);
        prWebClient.get("/pr", String.class);
        verify(1, getRequestedFor(urlMatching("/pr"))
                .withHeader(xAuthTokenKey, matching(xAuthToken))
                .withHeader(clientId, matching(clientIdValue)));
    }

    private Properties getFacilityInstanceProperties(String xAuthToken, String clientIdValue, String email, String password) {
        Properties facilityInstanceProperties = new Properties();
        facilityInstanceProperties.setProperty("facility.apiToken", xAuthToken);
        facilityInstanceProperties.setProperty("facility.clientId", clientIdValue);
        facilityInstanceProperties.setProperty("facility.email", email);
        facilityInstanceProperties.setProperty("facility.password", password);
        return facilityInstanceProperties;
    }

    private Properties getIdpProperties(String xAuthTokenKey, String clientId) {
        Properties idpProperties = new Properties();
        idpProperties.setProperty("idP.tokenName", xAuthTokenKey);
        idpProperties.setProperty("idP.clientIdName", clientId);
        idpProperties.setProperty(PropertyKeyConstants.IDP_SIGNIN_PATH, "signin");
        return idpProperties;
    }
}