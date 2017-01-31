package org.openmrs.module.shrclient.feeds.shr;

import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.service.EMREncounterService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.StringUtil;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

public class DefaultEncounterFeedWorker implements EncounterEventWorker {
    private EMRPatientService emrPatientService;
    private PropertiesReader propertiesReader;
    private ClientRegistry clientRegistry;
    private EMREncounterService emrEncounterService;

    private final Logger logger = Logger.getLogger(DefaultEncounterFeedWorker.class);

    public DefaultEncounterFeedWorker(EMRPatientService emrPatientService, EMREncounterService emrEncounterService, PropertiesReader propertiesReader,
                                      ClientRegistry clientRegistry) {
        this.emrPatientService = emrPatientService;
        this.propertiesReader = propertiesReader;
        this.clientRegistry = clientRegistry;
        this.emrEncounterService = emrEncounterService;
    }

    @Override
    public void process(EncounterEvent encounterEvent) {
        logger.info("Processing bundle with encounter id: " + encounterEvent.getEncounterId());
        String healthId = encounterEvent.getHealthId();
        try {
            org.openmrs.Patient emrPatient = emrPatientService.getEMRPatientByHealthId(healthId);
            if (null == emrPatient) {
                Patient patient = downloadActivePatient(healthId);
                if (!healthId.equals(patient.getHealthId()))
                    encounterEvent.setHealthId(patient.getHealthId());
                emrPatient = emrPatientService.createOrUpdateEmrPatient(patient);
            }

            emrEncounterService.createOrUpdateEncounter(emrPatient, encounterEvent);
        } catch (Exception e) {
            String message = String.format("Error occurred while trying to process encounter[%s] of patient[%s]",
                    encounterEvent.getEncounterId(), healthId);
            logger.error(message);
            throw new AtomFeedClientException(message, e);
        }
    }

    private Patient downloadActivePatient(String healthId) throws IdentityUnauthorizedException {
        RestClient mciClient = clientRegistry.getMCIClient();
        Patient patient = mciClient.get(StringUtil.ensureSuffix(propertiesReader.getMciPatientContext(), "/") + healthId, Patient.class);
        if (!patient.isActive() && patient.getMergedWith() != null) {
            patient = downloadActivePatient(patient.getMergedWith());
        }
        return patient;
    }
}
