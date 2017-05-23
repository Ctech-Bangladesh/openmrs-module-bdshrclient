package org.openmrs.module.shrclient.handlers;

import org.hl7.fhir.dstu3.model.Bundle;
import org.ict4h.atomfeed.client.domain.Event;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.EncounterIdMapping;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.PatientIdMapping;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SHRClient;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;

public class EncounterPushTest {

    private static final String HEALTH_ID = "1234567890123";
    private static final String encountersUrl = "patients/" + HEALTH_ID + "/encounters";
    @Mock
    private EncounterService encounterService;

    @Mock
    private SHRClient shrClient;

    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private SystemUserService systemUserService;

    @Mock
    private CompositionBundleCreator compositionBundleCreator;

    @Mock
    private IdMappingRepository idMappingRepository;

    @Mock
    private ClientRegistry clientRegistry;

    private EncounterPush encounterPush;

    @Before
    public void setup() throws IdentityUnauthorizedException {
        initMocks(this);
        when(clientRegistry.getSHRClient()).thenReturn(shrClient);
        encounterPush = new EncounterPush(
                encounterService,
                propertiesReader, compositionBundleCreator,
                idMappingRepository,
                clientRegistry,
                systemUserService);
    }

    @Test
    public void shouldProcessEncounterCreateEvent() throws IOException {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties());
        when(propertiesReader.getShrPatientEncPathPattern()).thenReturn("/patients/%s/encounters");
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(shrClient.post(anyString(), eq(bundle))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundleCreator.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(idMappingRepository.findByExternalId(facilityId, IdMappingType.FACILITY)).thenReturn(null);
        PatientIdMapping patientIdMapping = new PatientIdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, encountersUrl);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(encounterService).getEncounterByUuid(uuid);
        verify(shrClient).post(encountersUrl, bundle);
        ArgumentCaptor<EncounterIdMapping> encounterDdMappingArgumentCaptor = ArgumentCaptor.forClass(EncounterIdMapping.class);
        verify(idMappingRepository).saveOrUpdateIdMapping(encounterDdMappingArgumentCaptor.capture());

        EncounterIdMapping encounterIdMapping = encounterDdMappingArgumentCaptor.getValue();
        assertEquals("shr-uuid", encounterIdMapping.getExternalId());
        assertEquals("shr-uuid", encounterIdMapping.getExternalId());
        assertEquals("encounter", encounterIdMapping.getType());
        assertEquals("http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid", encounterIdMapping.getUri());
    }

    @Test
    public void shouldAddDrugOrdersToIdMapping() throws IOException {
        final String uuid = "123abc456";
        String facilityId = "10000069";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        DrugOrder drugOrder = new DrugOrder(10);
        OrderType drugOrderType = new OrderType();
        drugOrderType.setName(MRSProperties.MRS_DRUG_ORDER_TYPE);
        drugOrderType.setUuid(OrderType.DRUG_ORDER_TYPE_UUID);
        drugOrder.setOrderType(drugOrderType);
        openMrsEncounter.addOrder(drugOrder);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties());
        when(propertiesReader.getShrPatientEncPathPattern()).thenReturn("/patients/%s/encounters");
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(shrClient.post(anyString(), eq(bundle))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundleCreator.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(idMappingRepository.findByExternalId(facilityId, IdMappingType.FACILITY)).thenReturn(null);
        PatientIdMapping patientIdMapping = new PatientIdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, encountersUrl);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(encounterService).getEncounterByUuid(uuid);
        verify(shrClient).post(encountersUrl, bundle);
        ArgumentCaptor<IdMapping> idMappingArgumentCaptor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingRepository, times(2)).saveOrUpdateIdMapping(idMappingArgumentCaptor.capture());

        List<IdMapping> idMappings = idMappingArgumentCaptor.getAllValues();
        String orderUrl = "http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid" + "#MedicationRequest/" + drugOrder.getUuid();
        String externalId = String.format(MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, "shr-uuid", drugOrder.getUuid());
        assertTrue(containsIdMapping(idMappings, drugOrder.getUuid(), externalId, IdMappingType.MEDICATION_ORDER, orderUrl));
    }

    @Test
    public void shouldAddProcedureOrderToOrdersIdMapping() throws Exception {
        final String uuid = "123abc456";
        
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        Order procedureOrder = new Order(10);
        OrderType procedureOrderType = new OrderType();
        procedureOrderType.setName(MRSProperties.MRS_PROCEDURE_ORDER_TYPE);
        procedureOrder.setOrderType(procedureOrderType);
        openMrsEncounter.addOrder(procedureOrder);
        final Bundle bundle = new Bundle();

        PatientIdMapping patientIdMapping = new PatientIdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, encountersUrl);

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties());
        when(propertiesReader.getShrPatientEncPathPattern()).thenReturn("/patients/%s/encounters");
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(shrClient.post(anyString(), eq(bundle))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundleCreator.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(patientIdMapping);
        
        encounterPush.process(event);
        
        verify(shrClient).post(encountersUrl, bundle);
        ArgumentCaptor<IdMapping> idMappingArgumentCaptor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingRepository, times(2)).saveOrUpdateIdMapping(idMappingArgumentCaptor.capture());

        List<IdMapping> idMappings = idMappingArgumentCaptor.getAllValues();
        String orderUrl = "http://localhost:9997/patients/" + HEALTH_ID + "/encounters/shr-uuid" + "#ProcedureRequest/" + procedureOrder.getUuid();
        String externalId = String.format(MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, "shr-uuid", procedureOrder.getUuid());
        assertTrue(containsIdMapping(idMappings, procedureOrder.getUuid(), externalId, IdMappingType.PROCEDURE_REQUEST, orderUrl));
    }


    @Test
    public void shouldProcessEncounterUpdateEvent() throws Exception {
        final String uuid = "123abc456";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        DateTime dateCreated = new DateTime(openMrsEncounter.getDateCreated());
        openMrsEncounter.setDateChanged(dateCreated.plusMinutes(10).toDate());
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties());
        when(propertiesReader.getShrPatientEncPathPattern()).thenReturn("/patients/%s/encounters");
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingRepository.findByInternalId(uuid, IdMappingType.ENCOUNTER)).thenReturn(new EncounterIdMapping(uuid, "shr-uuid", "encounter", new Date(), null, openMrsEncounter.getDateCreated()));
        when(compositionBundleCreator.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        PatientIdMapping patientIdMapping = new PatientIdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, encountersUrl);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(shrClient).put("patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(idMappingRepository, times(1)).saveOrUpdateIdMapping(any(EncounterIdMapping.class));
    }

    @Test
    public void shouldNotProcessEncounterDownloadEvent() throws Exception {
        final String uuid = "123abc456";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);
        final Bundle bundle = new Bundle();

        when(propertiesReader.getShrProperties()).thenReturn(getShrProperties());
        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingRepository.findByInternalId(uuid, IdMappingType.ENCOUNTER)).thenReturn(new EncounterIdMapping(uuid, "shr-uuid", "encounter", null));
        when(compositionBundleCreator.create(any(Encounter.class), eq(HEALTH_ID), any(SystemProperties.class))).thenReturn(bundle);
        when(systemUserService.isUpdatedByOpenMRSShrSystemUser(openMrsEncounter)).thenReturn(true);
        PatientIdMapping patientIdMapping = new PatientIdMapping(openMrsEncounter.getPatient().getUuid(), HEALTH_ID, encountersUrl);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(patientIdMapping);

        encounterPush.process(event);

        verify(shrClient, never()).put("patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(shrClient, never()).post("patients/" + HEALTH_ID + "/encounters/shr-uuid", bundle);
        verify(idMappingRepository, never()).saveOrUpdateIdMapping(any(IdMapping.class));
    }

    @Test
    public void shouldGetEncounterUuidFromEventContent() throws Exception{
        final String uuid = "123abc456";
        final String content = "/openmrs/ws/rest/v1/encounter/" + uuid +
                "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))";
        assertEquals(uuid, encounterPush.getUuid(content));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotProcessEncounterIfPatientIsNotSynced() throws Exception{
        final String uuid = "123abc456";

        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter(uuid);

        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(idMappingRepository.findByInternalId(openMrsEncounter.getPatient().getUuid(), IdMappingType.PATIENT)).thenReturn(null);

        encounterPush.process(event);
    }

    private boolean containsIdMapping(List<IdMapping> idMappings, String internalId, String externalId, String type, String url) {
        for (IdMapping idMapping : idMappings) {
            if(idMapping.getInternalId().equals(internalId)
                    && idMapping.getExternalId().equals(externalId)
                    && idMapping.getType().equals(type)
                    && idMapping.getUri().equals(url))
                return true;
        }
        return false;
    }

    private org.openmrs.Encounter getOpenMrsEncounter(String uuid) {
        org.openmrs.Encounter openMrsEncounter = new org.openmrs.Encounter();
        openMrsEncounter.setUuid(uuid);
        openMrsEncounter.setCreator(new User(1));
        openMrsEncounter.setDateCreated(new Date());
        final Patient patient = new Patient();
        openMrsEncounter.setPatient(patient);
        return openMrsEncounter;
    }

    private Properties getShrProperties() {
        Properties shrProperties = new Properties();
        shrProperties.setProperty(SHR_REFERENCE_PATH, "http://localhost:9997");
        shrProperties.setProperty(SHR_PATIENT_ENC_PATH_PATTERN, "/patients/%s/encounters");
        return shrProperties;
    }
}
