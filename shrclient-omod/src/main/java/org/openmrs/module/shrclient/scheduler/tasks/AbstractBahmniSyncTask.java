package org.openmrs.module.shrclient.scheduler.tasks;

import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundle;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.feeds.openmrs.OpenMRSFeedClientFactory;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.handlers.EncounterPush;
import org.openmrs.module.shrclient.handlers.PatientPush;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.mapper.PatientMapper;
import org.openmrs.module.shrclient.service.impl.BbsCodeServiceImpl;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.scheduler.tasks.AbstractTask;

import java.net.URISyntaxException;

public abstract class AbstractBahmniSyncTask extends AbstractTask {
    private static final Logger log = Logger.getLogger(AbstractBahmniSyncTask.class);
    public static final String OPENMRS_PATIENT_FEED_URI = "openmrs://events/patient/recent";
    public static final String OPENMRS_ENCOUNTER_FEED_URI = "openmrs://events/encounter/recent";

    @Override
    public void execute() {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        IdentityStore identityStore = PlatformUtil.getIdentityStore();
        UserService userService = Context.getUserService();
        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);

        PatientPush patientPush = getPatientRegistry(propertiesReader, userService, clientRegistry);
        EncounterPush encounterPush = getEncounterRegistry(propertiesReader, userService, clientRegistry);
        executeBahmniTask(patientPush, encounterPush);
    }


    protected abstract void executeBahmniTask(PatientPush patientPush, EncounterPush encounterPush);

    private EncounterPush getEncounterRegistry(PropertiesReader propertiesReader, UserService userService,
                                               ClientRegistry clientRegistry) {
        try {
            return new EncounterPush(Context.getEncounterService(), userService,
                    propertiesReader,
                    PlatformUtil.getRegisteredComponent(CompositionBundle.class),
                    PlatformUtil.getRegisteredComponent(IdMappingsRepository.class),
                    clientRegistry);
        } catch (IdentityUnauthorizedException e) {
            throw handleInvalidIdentity(clientRegistry, e);

        }
    }

    private PatientPush getPatientRegistry(PropertiesReader propertiesReader, UserService userService,
                                           ClientRegistry clientRegistry) {
        try {
            return new PatientPush(
                    Context.getPatientService(),
                    userService,
                    Context.getPersonService(),
                    new PatientMapper(new BbsCodeServiceImpl()),
                    propertiesReader,
                    clientRegistry,
                    PlatformUtil.getRegisteredComponent(IdMappingsRepository.class));
        } catch (IdentityUnauthorizedException e) {
            throw handleInvalidIdentity(clientRegistry, e);
        }
    }

    private RuntimeException handleInvalidIdentity(ClientRegistry clientRegistry, IdentityUnauthorizedException e) {
        log.error("Invalid credentials or expired token. Clearing existing token if any.");
        clientRegistry.clearIdentityToken();
        e.printStackTrace();
        return new RuntimeException(e);
    }

    protected FeedClient getFeedClient(String uri, EventWorker worker) throws URISyntaxException {
        return new OpenMRSFeedClientFactory().getFeedClient(uri, worker);
    }
}
