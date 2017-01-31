package org.openmrs.module.shrclient.handlers;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.feeds.CatchmentFeedProcessor;
import org.openmrs.module.shrclient.feeds.mci.DefaultPatientFeedWorker;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.StringUtil;

import java.net.URISyntaxException;
import java.util.*;

import static org.openmrs.module.shrclient.util.Headers.ACCEPT_HEADER_KEY;
import static org.openmrs.module.shrclient.util.Headers.getHrmAccessTokenHeaders;
import static org.springframework.http.MediaType.APPLICATION_ATOM_XML;

public class PatientPull {

    private final Logger logger = Logger.getLogger(EncounterPull.class);
    private final PropertiesReader propertiesReader;
    private final IdentityStore identityStore;
    private final ClientRegistry clientRegistry;


    public PatientPull(PropertiesReader propertiesReader, IdentityStore identityStore) {
        this.propertiesReader = propertiesReader;
        this.identityStore = identityStore;
        this.clientRegistry = new ClientRegistry(propertiesReader, identityStore);

    }

    public void download() {
        ArrayList<String> patientFeedUrls = getMCIFeedUrls(propertiesReader);
        try {
            Map<String, String> requestHeaders = getRequestHeaders(propertiesReader);
            DefaultPatientFeedWorker defaultPatientFeedWorker = getPatientFeedWorker();
            for (String patientFeedUrl : patientFeedUrls) {
                CatchmentFeedProcessor catchmentFeedProcessor = new CatchmentFeedProcessor(patientFeedUrl, requestHeaders, clientRegistry);
                try {
                    catchmentFeedProcessor.process(defaultPatientFeedWorker, propertiesReader.getMciMaxFailedEvent());
                } catch (URISyntaxException e) {
                    logger.error("Couldn't download catchment patient. Error: ", e);
                }
            }
        } catch (IdentityUnauthorizedException e) {
            logger.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
        }
    }

    public void retry() {
        ArrayList<String> patientFeedUrls = getMCIFeedUrls(propertiesReader);
        try {
            Map<String, String> requestHeaders = getRequestHeaders(propertiesReader);
            DefaultPatientFeedWorker defaultPatientFeedWorker = getPatientFeedWorker();
            for (String patientFeedUrl : patientFeedUrls) {
                CatchmentFeedProcessor catchmentFeedProcessor = new CatchmentFeedProcessor(patientFeedUrl, requestHeaders, clientRegistry);
                try {
                    catchmentFeedProcessor.processFailedEvents(defaultPatientFeedWorker, propertiesReader.getMciMaxFailedEvent());
                } catch (URISyntaxException e) {
                    logger.error("Couldn't download catchment patient. Error: ", e);
                }
            }
        } catch (IdentityUnauthorizedException e) {
            logger.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
        }

    }

    private DefaultPatientFeedWorker getPatientFeedWorker() {
        EMRPatientService emrPatientService = PlatformUtil.getRegisteredComponent("hieEmrPatientService", EMRPatientService.class);
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        return new DefaultPatientFeedWorker(emrPatientService, propertiesReader, clientRegistry);
    }

    private Map<String, String> getRequestHeaders(PropertiesReader propertiesReader) throws IdentityUnauthorizedException {
        HashMap<String, String> headers = new HashMap<>();
        Properties facilityInstanceProperties = propertiesReader.getFacilityInstanceProperties();
        headers.putAll(getHrmAccessTokenHeaders(clientRegistry.getOrCreateIdentityToken(), facilityInstanceProperties));
        headers.put(ACCEPT_HEADER_KEY, APPLICATION_ATOM_XML.toString());
        return headers;

    }

    private ArrayList<String> getMCIFeedUrls(PropertiesReader propertiesReader) {
        String mciBaseURL = StringUtil.ensureSuffix(propertiesReader.getMciBaseUrl(), "/");
        String catchmentPathPattern = StringUtil.removePrefix(propertiesReader.getMCICatchmentPathPattern(), "/");
        List<String> facilityCatchments = propertiesReader.getFacilityCatchments();
        ArrayList<String> catchmentsUrls = new ArrayList<>();
        for (String facilityCatchment : facilityCatchments) {
            String catchmentUrl = mciBaseURL + String.format(catchmentPathPattern, facilityCatchment);
            catchmentsUrls.add(catchmentUrl);
        }
        return catchmentsUrls;
    }
}
