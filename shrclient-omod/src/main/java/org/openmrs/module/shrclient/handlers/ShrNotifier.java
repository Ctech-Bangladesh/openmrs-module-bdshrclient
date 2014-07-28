package org.openmrs.module.shrclient.handlers;


import org.apache.log4j.Logger;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.shrclient.OpenMRSFeedClientFactory;
import org.openmrs.module.shrclient.mapper.PatientMapper;
import org.openmrs.module.shrclient.service.impl.BbsCodeServiceImpl;
import org.openmrs.module.shrclient.util.FhirRestClient;
import org.openmrs.module.shrclient.util.RestClient;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

public class ShrNotifier {

    private static final Logger log = Logger.getLogger(ShrNotifier.class);

    private static final String OPENMRS_PATIENT_FEED_URI = "openmrs://events/patient/recent";
    private static final String OPENMRS_ENCOUNTER_FEED_URI = "openmrs://events/encounter/recent";

    public void processPatient() {
         process(OPENMRS_PATIENT_FEED_URI, new ShrPatientCreator(
                Context.getPatientService(),
                Context.getUserService(),
                Context.getPersonService(),
                new PatientMapper(Context.getService(AddressHierarchyService.class), new BbsCodeServiceImpl()),
                getMciWebClient()));
    }


    public void processEncounter() {
        process(OPENMRS_ENCOUNTER_FEED_URI, encounterUploader());
    }

    public void retryEncounter(){
        retry(OPENMRS_ENCOUNTER_FEED_URI, encounterUploader());
    }

    private ShrEncounterUploader encounterUploader() {
        return new ShrEncounterUploader(Context.getEncounterService(), Context.getUserService(), getShrWebClient(),
                getRegisteredComponent(CompositionBundleCreator.class));
    }

    private <T> T getRegisteredComponent(Class<T> clazz) {
        List<T> registeredComponents = Context.getRegisteredComponents(clazz);
        if (!registeredComponents.isEmpty()) {
            return registeredComponents.get(0);
        }
        return null;
    }

    private FeedClient feedClient(String uri, EventWorker worker) {
        OpenMRSFeedClientFactory factory = new OpenMRSFeedClientFactory();
        try {
            return factory.getFeedClient(new URI(uri), worker);
        } catch (URISyntaxException e) {
            log.error("Invalid URI. ", e);
            throw new RuntimeException(e);
        }
    }

    private void retry(String feedURI, EventWorker eventWorker){
        feedClient(feedURI, eventWorker).processFailedEvents();
    }

    private void process(String feedURI, EventWorker eventWorker) {
        feedClient(feedURI, eventWorker).processEvents();
    }

    private RestClient getMciWebClient() {
        Properties properties = getProperties("mci.properties");
        return new RestClient(properties.getProperty("mci.user"),
                properties.getProperty("mci.password"),
                properties.getProperty("mci.host"),
                properties.getProperty("mci.port"));
    }

    private FhirRestClient getShrWebClient() {
        Properties properties = getProperties("shr.properties");
        return new FhirRestClient(properties.getProperty("shr.user"),
                properties.getProperty("shr.password"),
                properties.getProperty("shr.host"),
                properties.getProperty("shr.port"));
    }

    Properties getProperties(String resource) {
        try {
            Properties properties = new Properties();
            final File file = new File(System.getProperty("user.home") + File.separator + "shr" + File.separator
                    + "config" + File.separator + resource);
            final InputStream inputStream;
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(resource);
            }
            properties.load(inputStream);
            return properties;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
