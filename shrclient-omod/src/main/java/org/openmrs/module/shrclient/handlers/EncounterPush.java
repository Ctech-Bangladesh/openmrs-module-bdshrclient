package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.hl7.fhir.dstu3.model.BaseResource;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.Encounter;
import org.openmrs.*;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ObservationType;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.*;
import org.openmrs.module.shrclient.util.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EncounterPush implements EventWorker {

    private static final Logger log = Logger.getLogger(EncounterPush.class);
    private IdMappingRepository idMappingsRepository;
    private CompositionBundleCreator compositionBundleCreator;
    private EncounterService encounterService;
    private PropertiesReader propertiesReader;
    private ClientRegistry clientRegistry;
    private SHRClient shrClient;
    private List<String> encounterUuidsProcessed;
    private SystemUserService systemUserService;

    public EncounterPush(EncounterService encounterService, PropertiesReader propertiesReader,
                         CompositionBundleCreator compositionBundleCreator, IdMappingRepository idMappingsRepository,
                         ClientRegistry clientRegistry,
                         SystemUserService systemUserService) throws IdentityUnauthorizedException {
        this.encounterService = encounterService;
        this.propertiesReader = propertiesReader;
        this.clientRegistry = clientRegistry;
        this.systemUserService = systemUserService;
        this.shrClient = clientRegistry.getSHRClient();
        this.compositionBundleCreator = compositionBundleCreator;
        this.idMappingsRepository = idMappingsRepository;
        this.encounterUuidsProcessed = new ArrayList<>();
    }

    @Override
    public void process(Event event) {
        log.debug("Event: [" + event + "]");
        try {
            String uuid = getUuid(event.getContent());
            org.openmrs.Encounter openMrsEncounter = encounterService.getEncounterByUuid(uuid);
            if (openMrsEncounter == null) {
                log.debug(String.format("No OpenMRS encounter exists with uuid: [%s].", uuid));
                return;
            }
            IdMapping mapping = getEncounterMapping(openMrsEncounter);
            if (!shouldUploadEncounter(openMrsEncounter)) {
                return;
            }

            String healthId = getPatientHealthId(openMrsEncounter.getPatient());
            log.debug("Uploading patient encounter to SHR : [ " + openMrsEncounter.getUuid() + "]");
            String shrEncounterId = mapping != null ? mapping.getExternalId() : null;
            SystemProperties systemProperties = new SystemProperties(
                    propertiesReader.getFrProperties(),
                    propertiesReader.getTrProperties(),
                    propertiesReader.getPrProperties(),
                    propertiesReader.getFacilityInstanceProperties(),
                    propertiesReader.getMciProperties(),
                    propertiesReader.getShrProperties(),
                    propertiesReader.getVisitTypeProperties());
            if (shrEncounterId != null) {
                pushEncounterUpdate(openMrsEncounter, shrEncounterId, healthId, systemProperties);
            } else {
                shrEncounterId = pushEncounterCreate(openMrsEncounter, healthId, systemProperties);
            }
            encounterUuidsProcessed.add(openMrsEncounter.getUuid());
            saveEncounterIdMapping(openMrsEncounter.getUuid(), healthId, shrEncounterId, systemProperties);
            saveIdMappingForDiagnosis(openMrsEncounter, healthId, shrEncounterId, systemProperties);
            saveIdMappingsForOrders(openMrsEncounter.getOrders(), healthId, shrEncounterId, systemProperties);
        } catch (Exception e) {
            log.error("Error while processing encounter sync event.", e);
            throw new RuntimeException(e);
        }
    }

    private void saveIdMappingsForOrders(Set<Order> orders, String healthId, String shrEncounterId, SystemProperties systemProperties) {
        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, healthId);
        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, shrEncounterId);
        for (Order order : orders) {
            if (order.getOrderType().getUuid().equals(OrderType.DRUG_ORDER_TYPE_UUID)) {
                orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new MedicationRequest().getResourceType().name());
                saveOrderIdMapping(shrEncounterId, order.getUuid(), IdMappingType.MEDICATION_ORDER, orderUrlReferenceIds, systemProperties);
            } else if (order.getOrderType().getName().equals(MRSProperties.MRS_PROCEDURE_ORDER_TYPE)) {
                orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceType().name());
                saveOrderIdMapping(shrEncounterId, order.getUuid(), IdMappingType.PROCEDURE_REQUEST, orderUrlReferenceIds, systemProperties);
            } else if (order.getOrderType().getName().equals(MRSProperties.MRS_LAB_ORDER_TYPE)) {
                orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceType().name());
                saveOrderIdMapping(shrEncounterId, order.getUuid(), IdMappingType.PROCEDURE_REQUEST, orderUrlReferenceIds, systemProperties);
            } else if (order.getOrderType().getName().equals(MRSProperties.MRS_RADIOLOGY_ORDER_TYPE)) {
                orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceType().name());
                saveOrderIdMapping(shrEncounterId, order.getUuid(), IdMappingType.PROCEDURE_REQUEST, orderUrlReferenceIds, systemProperties);
            }
        }
    }

    private void saveOrderIdMapping(String shrEncounterId, String orderUuid, String idMappingType, HashMap<String, String> orderUrlReferenceIds, SystemProperties systemProperties) {
        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, orderUuid);
        String orderUrl = new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
        String externalId = String.format(MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, orderUuid);
        idMappingsRepository.saveOrUpdateIdMapping(new OrderIdMapping(orderUuid, externalId, idMappingType, orderUrl, new Date()));
    }

    private void saveIdMappingForDiagnosis(Encounter openMrsEncounter, String healthId, String shrEncounterId, SystemProperties systemProperties) {
        HashMap<String, String> conditionUrlReferenceIds = new HashMap<>();
        conditionUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, healthId);
        conditionUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, shrEncounterId);
        conditionUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new Condition().getResourceType().name());
        EntityReference entityReference = new EntityReference();
        for (Obs obs : openMrsEncounter.getObsAtTopLevel(false)) {
            CompoundObservation compoundObservation = new CompoundObservation(obs);
            if (!compoundObservation.isOfType(ObservationType.VISIT_DIAGNOSES)) continue;
            conditionUrlReferenceIds.remove(EntityReference.REFERENCE_ID);
            conditionUrlReferenceIds.put(EntityReference.REFERENCE_ID, obs.getUuid());
            String diagnosisUrl = entityReference.build(BaseResource.class, systemProperties, conditionUrlReferenceIds);
            String externalId = String.format(MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, obs.getUuid());
            idMappingsRepository.saveOrUpdateIdMapping(new DiagnosisIdMapping(obs.getUuid(), externalId, diagnosisUrl));
        }
    }

    private void saveEncounterIdMapping(String openMrsEncounterUuid, String healthId, String shrEncounterId, SystemProperties systemProperties) {
        String shrEncounterUrl = getShrEncounterUrl(healthId, shrEncounterId, systemProperties);
        idMappingsRepository.saveOrUpdateIdMapping(new EncounterIdMapping(openMrsEncounterUuid, shrEncounterId, shrEncounterUrl, new Date()));
    }

    private String getShrEncounterUrl(String healthId, String shrEncounterId, SystemProperties systemProperties) {
        HashMap<String, String> encounterUrlReferenceIds = new HashMap<>();
        encounterUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, healthId);
        encounterUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrEncounterId);
        return new EntityReference().build(Encounter.class, systemProperties, encounterUrlReferenceIds);
    }

    private boolean shouldUploadEncounter(Encounter openMrsEncounter) {
        if (systemUserService.isUpdatedByOpenMRSShrSystemUser(openMrsEncounter)) {
            log.debug(String.format("Encounter downloaded from SHR.Ignoring encounter sync."));
            return false;
        }
        /*
        * To avoid processing of events for the same encounter in a job run, we store the encounter uuids processed in this job run.
        * It is cleared once the job has finished processing.
        */
        if (encounterUuidsProcessed.contains(openMrsEncounter.getUuid())) {
            log.debug(String.format("Enounter[%s] has been processed in the same job run before.", openMrsEncounter.getUuid()));
            return false;
        }
        return true;
    }

    private String pushEncounterCreate(Encounter openMrsEncounter, String healthId, SystemProperties systemProperties) throws IOException {
        try {
            String encPathPattern = StringUtil.removePrefix(propertiesReader.getShrPatientEncPathPattern(), "/");
            String shrEncounterCreateResponse = shrClient.post(String.format(encPathPattern, healthId),
                    compositionBundleCreator.create(openMrsEncounter, healthId, systemProperties));
            return getEncounterIdFromResponse(shrEncounterCreateResponse);
        } catch (IdentityUnauthorizedException e) {
            log.error("Clearing unauthorized identity token.");
            clientRegistry.clearIdentityToken();
            throw e;
        }
    }

    private void pushEncounterUpdate(Encounter openMrsEncounter, String shrEncounterId, String healthId, SystemProperties systemProperties) throws IOException {
        try {
            String encPathPattern = StringUtil.removePrefix(propertiesReader.getShrPatientEncPathPattern(), "/");
            String encPath = String.format(encPathPattern, healthId);
            String encUpdateUrl = String.format("%s/%s", encPath, shrEncounterId);
            shrClient.put(encUpdateUrl,
                    compositionBundleCreator.create(openMrsEncounter, healthId, systemProperties));
        } catch (IdentityUnauthorizedException e) {
            log.error("Clearing unauthorized identity token.");
            clientRegistry.clearIdentityToken();
            throw e;
        }
    }

    private String getEncounterIdFromResponse(String shrEncounterResponse) throws java.io.IOException {
        EncounterResponse encounterResponse = configureObjectMapper().readValue(shrEncounterResponse,
                EncounterResponse.class);
        //TODO : set the right url
        return encounterResponse.getEncounterId();
    }

    private ObjectMapper configureObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    private String getPatientHealthId(Patient emrPatient) {
        PatientIdMapping patientIdMapping = (PatientIdMapping) idMappingsRepository.findByInternalId(emrPatient.getUuid(), IdMappingType.PATIENT);
        if (patientIdMapping == null) {
            throw new AtomFeedClientException(String.format("Patient [%s] is not yet synced to MCI.",
                    emrPatient.getUuid()));
        }

        return patientIdMapping.getExternalId();
    }

    private IdMapping getEncounterMapping(Encounter openMrsEncounter) {
        return idMappingsRepository.findByInternalId(openMrsEncounter.getUuid(), IdMappingType.ENCOUNTER);
    }

    String getUuid(String content) {
        String encounterUuid = null;
        Pattern p = Pattern.compile("^\\/openmrs\\/ws\\/rest\\/v1\\/encounter\\/(.*)\\?v=.*");
        Matcher m = p.matcher(content);
        if (m.matches()) {
            encounterUuid = m.group(1);
        }
        return encounterUuid;
    }

    @Override
    public void cleanUp(Event event) {

    }
}
