package org.openmrs.module.shrclient.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.module.shrclient.mci.api.MciPatientUpdateResponse;
import org.openmrs.module.shrclient.mci.api.model.Address;
import org.openmrs.module.shrclient.mci.api.model.Patient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertEquals;

public class RestClientTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Test
    public void shouldGetPatient() throws Exception {
        RestClient restClient = new RestClient("user", "password", "localhost", "8089");
        final String acceptHeader = "accept";
        final String contentTypeJson = "application/json";
        final String authHeader = "Authorization";
        final String authHeaderValue = restClient.getAuthHeader();
        final String url = "/patient/100";

        final Patient patient = new Patient();
        patient.setGivenName("John");
        final Address address = new Address();
        address.setDivisionId("div-100");
        patient.setAddress(address);

        stubFor(get(urlEqualTo(url))
                .withHeader(acceptHeader, equalTo(contentTypeJson))
                .withHeader(authHeader, equalTo(authHeaderValue))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", contentTypeJson)
                        .withBody(toJson(patient))));

        Patient response = restClient.get(url, Patient.class);
        assertEquals(patient, response);

        verify(1, getRequestedFor(urlMatching(url))
                .withHeader(acceptHeader, matching(contentTypeJson))
                .withHeader(authHeader, matching(authHeaderValue)));
    }

    @Test
    public void shouldPostPatientAndProcessErrors() throws Exception {
        RestClient restClient = new RestClient("user", "password", "localhost", "8089");
        final String contentTypeHeader = "Content-Type";
        final String contentTypeJson = "application/json";
        final String authHeader = "Authorization";
        final String authHeaderValue = restClient.getAuthHeader();
        String url = "/patient";

        String response = "{" +
                "\"error_code\": 1000," +
                "\"http_status\": 400," +
                "\"message\": \"validation error\"," +
                "\"errors\": [{" +
                "\"code\": 1002," +
                "\"field\": \"nid\"," +
                "\"message\": \"invalid nid\"" +
                "}]}";

        stubFor(post(urlEqualTo(url))
                .withHeader(contentTypeHeader, equalTo(contentTypeJson))
                .withHeader(authHeader, equalTo(authHeaderValue))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader(contentTypeHeader, contentTypeJson)
                        .withBody(response)));


        final Patient patient = new Patient();
        patient.setGivenName("John");
        final Address address = new Address();
        address.setDivisionId("div-100");
        patient.setAddress(address);

        MciPatientUpdateResponse result = restClient.post(url, patient, MciPatientUpdateResponse.class);
        assertEquals("nid", result.getErrors()[0].getField());
        assertEquals(400, result.getHttpStatus());

        verify(1, postRequestedFor(urlMatching(url))
                .withRequestBody(equalToJson(toJson(patient)))
                .withHeader(contentTypeHeader, matching(contentTypeJson))
                .withHeader(authHeader, matching(authHeaderValue)));
    }

    @Test
    public void shouldPostPatientAndIdentifyHealthId() throws Exception {
        RestClient restClient = new RestClient("user", "password", "localhost", "8089");
        final String contentTypeHeader = "Content-Type";
        final String contentTypeJson = "application/json";
        final String authHeader = "Authorization";
        final String authHeaderValue = restClient.getAuthHeader();
        String url = "/patient";

        String response = "{\"http_status\": 201,\"id\":\"5916473242339508225\"}";

        stubFor(post(urlEqualTo(url))
                .withHeader(contentTypeHeader, equalTo(contentTypeJson))
                .withHeader(authHeader, equalTo(authHeaderValue))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader(contentTypeHeader, contentTypeJson)
                        .withBody(response)));


        final Patient patient = new Patient();
        patient.setGivenName("John");
        final Address address = new Address();
        address.setDivisionId("div-100");
        patient.setAddress(address);

        MciPatientUpdateResponse result = restClient.post(url, patient, MciPatientUpdateResponse.class);
        assertEquals("5916473242339508225", result.getHealthId());
        assertEquals(201, result.getHttpStatus());

        verify(1, postRequestedFor(urlMatching(url))
                .withRequestBody(equalToJson(toJson(patient)))
                .withHeader(contentTypeHeader, matching(contentTypeJson))
                .withHeader(authHeader, matching(authHeaderValue)));
    }


    @Test
    public void shouldPutPatientAndIdentifyHealthId() throws Exception {
        RestClient restClient = new RestClient("user", "password", "localhost", "8089");
        final String contentTypeHeader = "Content-Type";
        final String contentTypeJson = "application/json";
        final String authHeader = "Authorization";
        final String authHeaderValue = restClient.getAuthHeader();
        String url = "/patient/5916473242339508225";

        String response = "{\"http_status\": 202,\"id\":\"5916473242339508225\"}";

        stubFor(post(urlEqualTo(url))
                .withHeader(contentTypeHeader, equalTo(contentTypeJson))
                .withHeader(authHeader, equalTo(authHeaderValue))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader(contentTypeHeader, contentTypeJson)
                        .withBody(response)));

        stubFor(put(urlEqualTo(url))
                .withHeader(contentTypeHeader, equalTo(contentTypeJson))
                .withHeader(authHeader, equalTo(authHeaderValue))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader(contentTypeHeader, contentTypeJson)
                        .withBody(response)));


        final Patient patient = new Patient();
        patient.setGivenName("John");
        final Address address = new Address();
        address.setDivisionId("div-100");
        patient.setAddress(address);

        MciPatientUpdateResponse postResult = restClient.post(url, patient, MciPatientUpdateResponse.class);
        assertEquals("5916473242339508225", postResult.getHealthId());

        patient.setGivenName("Sumit");

        MciPatientUpdateResponse putResult = restClient.put(url, patient, MciPatientUpdateResponse.class);
        assertEquals("5916473242339508225", putResult.getHealthId());
        assertEquals(202, putResult.getHttpStatus());

        verify(1, putRequestedFor(urlMatching(url))
                .withRequestBody(equalToJson(toJson(patient)))
                .withHeader(contentTypeHeader, matching(contentTypeJson))
                .withHeader(authHeader, matching(authHeaderValue)));


    }


    private String toJson(Patient patient) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(patient);
    }
}
