package org.openmrs.module.shrclient.feeds.mci;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.exceptions.AtomFeedClientException;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.module.shrclient.feeds.shr.DefaultEncounterFeedWorker;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.PropertiesReader;

import java.io.IOException;


public class DefaultPatientFeedWorker implements EventWorker {
    private final EMRPatientService emrPatientService;

    private final Logger logger = Logger.getLogger(DefaultEncounterFeedWorker.class);

    public DefaultPatientFeedWorker(EMRPatientService emrPatientService) {
        this.emrPatientService = emrPatientService;
    }

    @Override
    public void process(Event event) {
        try {
            String content = event.getContent();
            Patient patient = new ObjectMapper().readValue(content, Patient.class);
            emrPatientService.createOrUpdateEmrPatient(patient);

        } catch (IOException e) {
            String message = String.format("Error occurred while trying to process  patient feed");
            logger.error(message);
            throw new AtomFeedClientException(message, e);
        }
    }

    @Override
    public void cleanUp(Event event) {

    }
}
