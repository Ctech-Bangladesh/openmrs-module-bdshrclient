package org.openmrs.module.shrclient.handlers;


import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.feeds.shr.DefaultEncounterFeedWorker;
import org.openmrs.module.shrclient.feeds.CatchmentFeedProcessor;
import org.openmrs.module.shrclient.feeds.shr.ShrFeedEventWorker;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.service.EMREncounterService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.StringUtil;

import java.net.URISyntaxException;
import java.util.*;

import static org.openmrs.module.shrclient.util.Headers.ACCEPT_HEADER_KEY;
import static org.openmrs.module.shrclient.util.Headers.getHrmAccessTokenHeaders;
import static org.springframework.http.MediaType.APPLICATION_ATOM_XML;

public class EncounterPull {

    private final Logger logger = Logger.getLogger(EncounterPull.class);
    private PropertiesReader propertiesReader;
    private IdentityStore identityStore;
    private ClientRegistry clientRegistry;

    public EncounterPull(PropertiesReader propertiesReader, IdentityStore identityStore) {
        this.propertiesReader = propertiesReader;
        this.identityStore = identityStore;
        clientRegistry = new ClientRegistry(propertiesReader, identityStore);
    }

    public void download() {
        ArrayList<String> encounterFeedUrls = getEncounterFeedUrls(propertiesReader);
        try {
            Map<String, String> requestHeaders = getRequestHeaders(propertiesReader);
            DefaultEncounterFeedWorker defaultEncounterFeedWorker = getEncounterFeedWorker();
            for (String encounterFeedUrl : encounterFeedUrls) {
                CatchmentFeedProcessor feedProcessor =
                        new CatchmentFeedProcessor(encounterFeedUrl, requestHeaders,
                                clientRegistry, propertiesReader);
                try {
                    feedProcessor.process(new ShrFeedEventWorker(defaultEncounterFeedWorker));
                } catch (URISyntaxException e) {
                    logger.error("Couldn't download catchment encounters. Error: ", e);
                }
            }
        } catch (IdentityUnauthorizedException e) {
            logger.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
        }
    }

    private DefaultEncounterFeedWorker getEncounterFeedWorker() {
        EMRPatientService emrPatientService = PlatformUtil.getRegisteredComponent("hieEmrPatientService", EMRPatientService.class);
        EMREncounterService emrEncounterService = PlatformUtil.getRegisteredComponent("hieEmrEncounterService", EMREncounterService.class);
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        return new DefaultEncounterFeedWorker(emrPatientService, emrEncounterService, propertiesReader, clientRegistry);
    }

    private HashMap<String, String> getRequestHeaders(PropertiesReader propertiesReader) throws IdentityUnauthorizedException {
        HashMap<String, String> headers = new HashMap<>();
        Properties facilityInstanceProperties = propertiesReader.getFacilityInstanceProperties();
        headers.putAll(getHrmAccessTokenHeaders(clientRegistry.getOrCreateIdentityToken(), facilityInstanceProperties));
        headers.put(ACCEPT_HEADER_KEY, APPLICATION_ATOM_XML.toString());
        return headers;
    }

    public ArrayList<String> getEncounterFeedUrls(PropertiesReader propertiesReader) {
        String shrBaseUrl = StringUtil.ensureSuffix(propertiesReader.getShrBaseUrl(), "/");
        String catchmentPathPattern = StringUtil.removePrefix(propertiesReader.getShrCatchmentPathPattern(), "/");
        List<String> facilityCatchments = propertiesReader.getFacilityCatchments();
        ArrayList<String> catchmentsUrls = new ArrayList<>();
        for (String facilityCatchment : facilityCatchments) {
            String catchmentUrl = shrBaseUrl + String.format(catchmentPathPattern, facilityCatchment);
            catchmentsUrls.add(catchmentUrl);
        }
        return catchmentsUrls;
    }

    public void retry() {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        ArrayList<String> encounterFeedUrls = getEncounterFeedUrls(propertiesReader);
        try {
            Map<String, String> requestProperties = getRequestHeaders(propertiesReader);
            DefaultEncounterFeedWorker defaultEncounterFeedWorker = getEncounterFeedWorker();
            for (String encounterFeedUrl : encounterFeedUrls) {
                CatchmentFeedProcessor feedProcessor =
                        new CatchmentFeedProcessor(encounterFeedUrl, requestProperties,
                                clientRegistry, propertiesReader);
                try {
                    feedProcessor.processFailedEvents(new ShrFeedEventWorker(defaultEncounterFeedWorker));
                } catch (URISyntaxException e) {
                    logger.error("Couldn't download catchment encounters. Error: ", e);
                }
            }
        } catch (IdentityUnauthorizedException e) {
            logger.info("Clearing unauthorized identity token.");
            identityStore.clearToken();
        }
    }
}
