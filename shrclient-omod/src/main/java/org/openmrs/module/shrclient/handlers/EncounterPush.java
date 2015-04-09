package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.api.UserService;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundle;
import org.openmrs.module.fhir.utils.Constants;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.EncounterResponse;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SHRClient;
import org.openmrs.module.shrclient.util.StringUtil;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EncounterPush implements EventWorker {

    private static final Logger log = Logger.getLogger(EncounterPush.class);

    private CompositionBundle compositionBundle;
    private IdMappingsRepository idMappingsRepository;

    private EncounterService encounterService;
    private PropertiesReader propertiesReader;
    private ClientRegistry clientRegistry;
    private SHRClient shrClient;
    private UserService userService;

    public EncounterPush(EncounterService encounterService, UserService userService, PropertiesReader propertiesReader,
                         CompositionBundle compositionBundle, IdMappingsRepository idMappingsRepository,
                         ClientRegistry clientRegistry) throws IdentityUnauthorizedException {
        this.encounterService = encounterService;
        this.propertiesReader = propertiesReader;
        this.clientRegistry = clientRegistry;
        this.shrClient = clientRegistry.getSHRClient();
        this.userService = userService;
        this.compositionBundle = compositionBundle;
        this.idMappingsRepository = idMappingsRepository;
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
            if (!shouldSyncEncounter(openMrsEncounter)) {
                return;
            }
            String healthId = getHealthIdAttribute(openMrsEncounter.getPatient());
            log.debug("Uploading patient encounter to SHR : [ " + openMrsEncounter.getUuid() + "]");
            String shrEncounterUuid = pushEncounter(openMrsEncounter, healthId);
            EncounterResponse encounterResponse = configureObjectMapper().readValue(shrEncounterUuid,
                    EncounterResponse.class);
            //TODO : set the right url
            String externalUuid = encounterResponse.getEncounterId();
            idMappingsRepository.saveMapping(new IdMapping(openMrsEncounter.getUuid(), externalUuid,
                    Constants.ID_MAPPING_ENCOUNTER_TYPE, formatEncounterUrl(healthId, externalUuid)));
        } catch (Exception e) {
            log.error("Error while processing encounter sync event.", e);
            throw new RuntimeException(e);
        }
    }

    private String formatEncounterUrl(String healthId, String externalUuid) {
        String shrBaseUrl = StringUtil.ensureSuffix(propertiesReader.getShrBaseUrl(), "/");
        String encPathPattern = StringUtil.removePrefix(propertiesReader.getShrPatientEncPathPattern(), "/");
        return StringUtil.ensureSuffix(shrBaseUrl + String.format(encPathPattern, healthId), "/") + externalUuid;
    }

    private String pushEncounter(Encounter openMrsEncounter, String healthId) throws IdentityUnauthorizedException {
        try {
            String encPathPattern = StringUtil.removePrefix(propertiesReader.getShrPatientEncPathPattern(), "/");
            return shrClient.post(String.format(encPathPattern, healthId),
                    compositionBundle.create(openMrsEncounter,
                            new SystemProperties(propertiesReader.getBaseUrls(),
                                    propertiesReader.getFrProperties(),
                                    propertiesReader.getTrProperties(),
                                    propertiesReader.getPrProperties(),
                                    propertiesReader.getFacilityInstanceProperties(),
                                    propertiesReader.getMciProperties(),
                                    propertiesReader.getShrProperties())));
        } catch (IdentityUnauthorizedException e) {
            log.error("Clearing unauthorized identity token.");
            clientRegistry.clearIdentityToken();
            throw e;
        }
    }

    private ObjectMapper configureObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    private String getHealthIdAttribute(Patient emrPatient) {
        PersonAttribute healthIdAttribute = emrPatient.getAttribute(Constants.HEALTH_ID_ATTRIBUTE);
        if ((healthIdAttribute == null) || (StringUtils.isBlank(healthIdAttribute.getValue()))) {
            throw new AtomFeedClientException(String.format("Patient [%s] is not yet synced to MCI.",
                    emrPatient.getUuid()));
        }

        return healthIdAttribute.getValue();
    }

    private boolean shouldSyncEncounter(org.openmrs.Encounter openMrsEncounter) {
        if (idMappingsRepository.findByInternalId(openMrsEncounter.getUuid()) == null) {
            User changedByUser = openMrsEncounter.getChangedBy();
            if (changedByUser == null) {
                changedByUser = openMrsEncounter.getCreator();
            }
            User shrClientSystemUser = userService.getUserByUsername(org.openmrs.module.fhir.utils.Constants
                    .SHR_CLIENT_SYSTEM_NAME);
            return !shrClientSystemUser.getId().equals(changedByUser.getId());
        }
        return false;
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
