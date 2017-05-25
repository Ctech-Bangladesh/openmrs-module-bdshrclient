package org.openmrs.module.shrclient.handlers;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.ict4h.atomfeed.client.domain.Event;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.FhirBundleContextHolder;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;
import static org.openmrs.module.fhir.utils.FHIREncounterUtil.getIdPart;
import static org.openmrs.module.shrclient.util.Headers.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EncounterPushIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private CompositionBundleCreator compositionBundleCreator;

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Autowired
    private IdentityStore identityStore;

    @Autowired
    private PropertiesReader propertiesReader;

    private EncounterPush encounterPush;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9997);
    private final String clientIdValue = "12345";
    private final String email = "email@gmail.com";
    private final String accessToken = UUID.randomUUID().toString();

    @Before
    public void setUp() throws Exception {

        String response = "{\"access_token\" : \"" + accessToken + "\"}";
        String xAuthToken = "xyz";
        givenThat(post(urlEqualTo("/signin"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(xAuthToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(response)));

        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);
        encounterPush = new EncounterPush(encounterService, propertiesReader,
                compositionBundleCreator, idMappingRepository,
                clientRegistry, systemUserService);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldPushEncounter() throws Exception {
        executeDataSet("testDataSets/encounterUploadTestDS.xml");

        String shrEncounterId = "shr-enc-id-1";
        givenThat(post(urlEqualTo("/patients/1234512345123/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));


        String encounterUuid = "6d0af6767-707a-4629-9850-f15206e63ab0";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        verify(1, postRequestedFor(urlEqualTo("/patients/1234512345123/encounters"))
                .withHeader(AUTH_TOKEN_KEY, matching(accessToken))
                .withHeader(CLIENT_ID_KEY, matching(clientIdValue))
                .withHeader(FROM_KEY, matching(email)));

        IdMapping idMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(idMapping);
        assertEquals(encounterUuid, idMapping.getInternalId());
        assertNotNull(idMapping.getLastSyncDateTime());
    }

    @Test
    public void shouldAddMedicationRequestToIdMappings() throws Exception {
        executeDataSet("testDataSets/drugOrderDS.xml");

        String shrEncounterId = "shr_enc_id_2";
        givenThat(post(urlEqualTo("/patients/98104750156/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));

        String encounterUuid = "6d0af6767-123r-4629-9850-ttc2c6e63ab0";
        String orderUuid = "amkbja86-1e43-g1f3-9qw0-ccc2c6c63ab0";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        final List<LoggedRequest> loggedRequests = findAll(postRequestedFor(urlEqualTo("/patients/98104750156/encounters")));
        assertEquals(1, loggedRequests.size());
        String bundleXML = loggedRequests.get(0).getBodyAsString();
        Bundle bundle = (Bundle) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
        final Resource resourceByReference = FHIRBundleHelper.findResourceByReference(bundle, new Reference("urn:uuid:" + orderUuid));
        assertNotNull(resourceByReference);

        IdMapping encounterIdMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterIdMapping);
        assertEquals(encounterUuid, encounterIdMapping.getInternalId());
        assertNotNull(encounterIdMapping.getLastSyncDateTime());

        IdMapping orderMapping = idMappingRepository.findByInternalId(orderUuid, IdMappingType.MEDICATION_ORDER);
        assertNotNull(orderMapping);
        assertEquals(orderUuid, orderMapping.getInternalId());
        String expectedExternalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, getIdPart(resourceByReference.getId()));
        assertEquals(expectedExternalId, orderMapping.getExternalId());
        String orderUrl = encounterIdMapping.getUri() + "#MedicationRequest/" + orderUuid;
        assertEquals(orderUrl, orderMapping.getUri());
    }

    @Test
    public void shouldAddDiagnosisConditionToIdMappings() throws Exception {
        executeDataSet("testDataSets/diagnosisTestDS.xml");

        String shrEncounterId = "shr_enc_id_3";
        givenThat(post(urlEqualTo("/patients/98104750156/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));

        String encounterUuid = "6d0af6767-707a-4629-9850-f15206e63ab0";
        String visitDiagnosisObsUuid = "ef4554cb-2225-471a-9cd7-1434552c337c";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        final List<LoggedRequest> loggedRequests = findAll(postRequestedFor(urlEqualTo("/patients/98104750156/encounters")));
        assertEquals(1, loggedRequests.size());
        String bundleXML = loggedRequests.get(0).getBodyAsString();
        Bundle bundle = (Bundle) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
        final Resource resourceByReference = FHIRBundleHelper.findResourceByReference(bundle, new Reference("urn:uuid:" + visitDiagnosisObsUuid));
        assertNotNull(resourceByReference);

        IdMapping encounterIdMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterIdMapping);
        assertEquals(encounterUuid, encounterIdMapping.getInternalId());
        assertNotNull(encounterIdMapping.getLastSyncDateTime());

        IdMapping diagnosisMapping = idMappingRepository.findByInternalId(visitDiagnosisObsUuid, IdMappingType.DIAGNOSIS);
        assertNotNull(diagnosisMapping);
        assertEquals(visitDiagnosisObsUuid, diagnosisMapping.getInternalId());
        String expectedExternalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, getIdPart(resourceByReference.getId()));
        assertEquals(expectedExternalId, diagnosisMapping.getExternalId());
        String diagnosisUrl = encounterIdMapping.getUri() + "#Condition/" + visitDiagnosisObsUuid;
        assertEquals(diagnosisUrl, diagnosisMapping.getUri());
    }

    @Test
    public void shouldAddProcedureOrderToIdMappings() throws Exception {
        executeDataSet("testDataSets/procedureOrderDS.xml");

        String shrEncounterId = "shr_enc_id_3";
        givenThat(post(urlEqualTo("/patients/98104750156/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));

        String encounterUuid = "6d0af6767-12se-4629-9850-f15206e63ab0";
        String procedureOrderUuid = "6d0ae386-707a-43ed-16ws-f15206e63ab0";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        final List<LoggedRequest> loggedRequests = findAll(postRequestedFor(urlEqualTo("/patients/98104750156/encounters")));
        assertEquals(1, loggedRequests.size());
        String bundleXML = loggedRequests.get(0).getBodyAsString();
        Bundle bundle = (Bundle) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
        final Resource resourceByReference = FHIRBundleHelper.findResourceByReference(bundle, new Reference("urn:uuid:" + procedureOrderUuid));
        assertNotNull(resourceByReference);

        IdMapping encounterIdMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterIdMapping);
        assertEquals(encounterUuid, encounterIdMapping.getInternalId());
        assertNotNull(encounterIdMapping.getLastSyncDateTime());

        IdMapping procedureOrderMapping = idMappingRepository.findByInternalId(procedureOrderUuid, IdMappingType.PROCEDURE_REQUEST);
        assertNotNull(procedureOrderMapping);
        assertEquals(procedureOrderUuid, procedureOrderMapping.getInternalId());
        String expectedExternalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, getIdPart(resourceByReference.getId()));
        assertEquals(expectedExternalId, procedureOrderMapping.getExternalId());
        String procedureUrl = encounterIdMapping.getUri() + "#ProcedureRequest/" + procedureOrderUuid;
        assertEquals(procedureUrl, procedureOrderMapping.getUri());
    }

    @Test
    public void shouldAddALabProcedureRequestToIdMappings() throws Exception {
        executeDataSet("testDataSets/labOrderDS.xml");

        String shrEncounterId = "shr_enc_id_4";
        givenThat(post(urlEqualTo("/patients/98104750156/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));

        String encounterUuid = "6d0af6767-707a-4629-9850-f15206e63ab0";
        String procedureRequestId = "6d0ae386-707a-f123-9850-f15206e63ab0";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        final List<LoggedRequest> loggedRequests = findAll(postRequestedFor(urlEqualTo("/patients/98104750156/encounters")));
        assertEquals(1, loggedRequests.size());
        String bundleXML = loggedRequests.get(0).getBodyAsString();
        Bundle bundle = (Bundle) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
        final Resource resourceByReference = FHIRBundleHelper.findResourceByReference(bundle, new Reference("urn:uuid:" + procedureRequestId + "#123"));
        assertNotNull(resourceByReference);

        IdMapping encounterIdMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterIdMapping);
        assertEquals(encounterUuid, encounterIdMapping.getInternalId());
        assertNotNull(encounterIdMapping.getLastSyncDateTime());

        IdMapping procedureRequestMapping = idMappingRepository.findByInternalId(procedureRequestId, IdMappingType.PROCEDURE_REQUEST);
        assertNotNull(procedureRequestMapping);
        assertEquals(procedureRequestId, procedureRequestMapping.getInternalId());
        String expectedExternalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, getIdPart(resourceByReference.getId()));
        //todo:need to make sure whether this 123 should be there or not
        assertEquals(expectedExternalId, procedureRequestMapping.getExternalId() + "#123");
        String procedureRequestUrl = encounterIdMapping.getUri() + "#ProcedureRequest/" + procedureRequestId;
        assertEquals(procedureRequestUrl, procedureRequestMapping.getUri());
    }

    @Test
    public void shouldAddRadiologyOrderToIdMappings() throws Exception {
        executeDataSet("testDataSets/radiologyOrderDS.xml");

        String shrEncounterId = "shr_enc_id_5";
        givenThat(post(urlEqualTo("/patients/98104750157/encounters"))
                .withHeader(AUTH_TOKEN_KEY, equalTo(accessToken))
                .withHeader(CLIENT_ID_KEY, equalTo(clientIdValue))
                .withHeader(FROM_KEY, equalTo(email))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"encounterId\" : \"" + shrEncounterId + "\"}")));

        String encounterUuid = "6d0af6767-707a-4629-9850-235216e63ab0";
        String procedureRequestId = "6d0ae396-efab-4629-1930-f15206e63ab0";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + encounterUuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");

        encounterPush.process(event);

        final List<LoggedRequest> loggedRequests = findAll(postRequestedFor(urlEqualTo("/patients/98104750157/encounters")));
        assertEquals(1, loggedRequests.size());
        String bundleXML = loggedRequests.get(0).getBodyAsString();
        Bundle bundle = (Bundle) FhirBundleContextHolder.getFhirContext().newXmlParser().parseResource(bundleXML);
        final Resource resourceByReference = FHIRBundleHelper.findResourceByReference(bundle, new Reference("urn:uuid:" + procedureRequestId));
        assertNotNull(resourceByReference);

        IdMapping encounterIdMapping = idMappingRepository.findByExternalId(shrEncounterId, IdMappingType.ENCOUNTER);
        assertNotNull(encounterIdMapping);
        assertEquals(encounterUuid, encounterIdMapping.getInternalId());
        assertNotNull(encounterIdMapping.getLastSyncDateTime());

        IdMapping procedureRequestMapping = idMappingRepository.findByInternalId(procedureRequestId, IdMappingType.PROCEDURE_REQUEST);
        assertNotNull(procedureRequestMapping);
        assertEquals(procedureRequestId, procedureRequestMapping.getInternalId());
        String expectedExternalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, getIdPart(resourceByReference.getId()));
        assertEquals(expectedExternalId, procedureRequestMapping.getExternalId());
        String procedureRequestUrl = encounterIdMapping.getUri() + "#ProcedureRequest/" + procedureRequestId;
        assertEquals(procedureRequestUrl, procedureRequestMapping.getUri());
    }
}
