package org.openmrs.module.shrclient.scheduler.tasks;

import org.apache.log4j.Logger;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.ict4h.atomfeed.client.service.FeedClient;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.feeds.openmrs.OpenMRSFeedClientFactory;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.handlers.EncounterPush;
import org.openmrs.module.shrclient.handlers.PatientPush;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.mapper.PatientMapper;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.scheduler.tasks.AbstractTask;

import java.net.URISyntaxException;

public abstract class AbstractBahmniSyncTask extends AbstractTask {
    private static final Logger log = Logger.getLogger(AbstractBahmniSyncTask.class);
    public static final String OPENMRS_PATIENT_FEED_URI = "openmrs://events/patient/recent";
    public static final String OPENMRS_ENCOUNTER_FEED_URI = "openmrs://events/OpenMRSEncounter/recent";

    @Override
    public void execute() {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        IdentityStore identityStore = PlatformUtil.getIdentityStore();
        SystemUserService systemUserService = PlatformUtil.getRegisteredComponent(SystemUserService.class);
        ClientRegistry clientRegistry = new ClientRegistry(propertiesReader, identityStore);
        PatientPush patientPush = getPatientRegistry(propertiesReader, systemUserService, clientRegistry);
        EncounterPush encounterPush = getEncounterRegistry(propertiesReader, systemUserService, clientRegistry);
        executeBahmniTask(patientPush, encounterPush, propertiesReader);
    }

    protected abstract void executeBahmniTask(PatientPush patientPush, EncounterPush encounterPush, PropertiesReader propertiesReader);

    private EncounterPush getEncounterRegistry(PropertiesReader propertiesReader, SystemUserService systemUserService,
                                               ClientRegistry clientRegistry) {
        try {
            return new EncounterPush(Context.getEncounterService(),
                    propertiesReader,
                    PlatformUtil.getRegisteredComponent(CompositionBundleCreator.class),
                    PlatformUtil.getIdMappingsRepository(),
                    clientRegistry,
                    systemUserService);
        } catch (IdentityUnauthorizedException e) {
            throw handleInvalidIdentity(clientRegistry, e);

        }
    }

    private PatientPush getPatientRegistry(PropertiesReader propertiesReader, SystemUserService systemUserService,
                                           ClientRegistry clientRegistry) {
        try {
            IdMappingRepository idMappingsRepository = PlatformUtil.getIdMappingsRepository();
            return new PatientPush(
                    Context.getPatientService(),
                    systemUserService,
                    Context.getPersonService(),
                    new PatientMapper(new BbsCodeService(), idMappingsRepository),
                    propertiesReader,
                    clientRegistry,
                    idMappingsRepository,
                    Context.getProviderService(), Context.getLocationService());
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

    protected FeedClient getFeedClient(String uri, EventWorker worker, int maxFailedEvents) throws URISyntaxException {
        return new OpenMRSFeedClientFactory().getFeedClient(uri, worker, maxFailedEvents);
    }
}
