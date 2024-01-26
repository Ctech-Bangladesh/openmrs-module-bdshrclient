package org.openmrs.module.shrclient.advice;

import org.apache.commons.lang3.StringUtils;
import org.ict4h.atomfeed.server.repository.jdbc.AllEventRecordsQueueJdbcImpl;
import org.ict4h.atomfeed.server.service.Event;
import org.ict4h.atomfeed.server.service.EventService;
import org.ict4h.atomfeed.server.service.EventServiceImpl;
import org.ict4h.atomfeed.transaction.AFTransactionWorkWithoutResult;
import org.joda.time.DateTime;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.atomfeed.transaction.support.AtomFeedSpringTransactionManager;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_SHR_CATEGORY_EVENT;
import static org.openmrs.module.shrclient.advice.ShrEncounterAdvice.ENCOUNTER_REST_URL;

@Component
public class SHREncounterEventService {
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    public SHREncounterEventService(GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    public void raiseShrEncounterDownloadEvent(Encounter newEmrEncounter) {
        String shrEncounterEventCategory = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_CATEGORY_EVENT);
        if (StringUtils.isNotBlank(shrEncounterEventCategory)) {
            String url = String.format(ENCOUNTER_REST_URL, newEmrEncounter.getUuid());
            final Event event = new Event(UUID.randomUUID().toString(), shrEncounterEventCategory, DateTime.now(), (URI) null, url, shrEncounterEventCategory);
            AtomFeedSpringTransactionManager atomFeedSpringTransactionManager = findTransactionManager();
            final EventService eventService = getEventService(atomFeedSpringTransactionManager);
            atomFeedSpringTransactionManager.executeWithTransaction(
                    new AFTransactionWorkWithoutResult() {
                        @Override
                        protected void doInTransaction() {
                            eventService.notify(event);
                        }

                        @Override
                        public PropagationDefinition getTxPropagationDefinition() {
                            return PropagationDefinition.PROPAGATION_REQUIRED;
                        }
                    }
            );
        }

    }

    private AtomFeedSpringTransactionManager findTransactionManager() {
        PlatformTransactionManager platformTransactionManager = getSpringPlatformTransactionManager();
        return new AtomFeedSpringTransactionManager(platformTransactionManager);
    }

    private PlatformTransactionManager getSpringPlatformTransactionManager() {
        List<PlatformTransactionManager> platformTransactionManagers = Context.getRegisteredComponents(PlatformTransactionManager.class);
        return platformTransactionManagers.get(0);
    }

    private EventServiceImpl getEventService(AtomFeedSpringTransactionManager atomFeedSpringTransactionManager) {
        AllEventRecordsQueueJdbcImpl records = new AllEventRecordsQueueJdbcImpl(atomFeedSpringTransactionManager);
        return new EventServiceImpl(records);
    }
}
