package org.openmrs.module.shrclient.advice;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.ict4h.atomfeed.server.repository.jdbc.AllEventRecordsQueueJdbcImpl;
import org.ict4h.atomfeed.server.service.Event;
import org.ict4h.atomfeed.server.service.EventService;
import org.ict4h.atomfeed.server.service.EventServiceImpl;
import org.ict4h.atomfeed.transaction.AFTransactionWorkWithoutResult;
import org.joda.time.DateTime;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.atomfeed.transaction.support.AtomFeedSpringTransactionManager;
import org.openmrs.module.fhir.utils.OMRSLocationService;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public class ShrEncounterAdvice implements AfterReturningAdvice {

    public static final String ENCOUNTER_REST_URL = "/openmrs/ws/rest/v1/encounter/%s?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))";
    public static final String TITLE = "OpenMRSEncounter";
    public static final String CATEGORY = "OpenMRSEncounter";
    private static final String SAVE_METHOD = "saveEncounter";
    private final EventService eventService;
    private AtomFeedSpringTransactionManager atomFeedSpringTransactionManager;
    private EncounterAdviceState encounterAdviceState;
    private static final Logger logger = Logger.getLogger(ShrEncounterAdvice.class);
    private OMRSLocationService locationService;

    public ShrEncounterAdvice() {
        atomFeedSpringTransactionManager = findTransactionManager();
        eventService = getEventService(atomFeedSpringTransactionManager);
        encounterAdviceState = new EncounterAdviceState();
        //need to ask how to inject it
        locationService = PlatformUtil.getRegisteredComponent(OMRSLocationService.class);
    }

    public ShrEncounterAdvice(AtomFeedSpringTransactionManager atomFeedSpringTransactionManager,
                              EventService eventService, EncounterAdviceState encounterAdviceState,
                              OMRSLocationService locationService) {
        this.atomFeedSpringTransactionManager = atomFeedSpringTransactionManager;
        this.eventService = eventService;
        this.encounterAdviceState = encounterAdviceState;
        this.locationService = locationService;
    }

    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
        if (!method.getName().equals(SAVE_METHOD)) return;
        final Object encounterUuid = PropertyUtils.getProperty(returnValue, "uuid");
        if (encounterUuid == null) return;
        final boolean alreadyRaisedEvent = encounterAdviceState.hasAlreadyProcessedEncounter((String) encounterUuid);
        if (alreadyRaisedEvent) {
            //we have already raised an event
            logger.warn("Not creating an Event since it is deduced that its already raised for Encounter:" + encounterUuid);
            return;
        }

        Object locationProperty = PropertyUtils.getProperty(returnValue, "location");
        if (locationProperty != null) {
            Location location = (Location) locationProperty;
            if (!locationService.isLoginLocation(location)) {
                return;
            }
        }

        String url = String.format(ENCOUNTER_REST_URL, encounterUuid);
        final Event event = new Event(UUID.randomUUID().toString(), TITLE, DateTime.now(), (URI) null, url, CATEGORY);
        Object encounterType = PropertyUtils.getProperty(returnValue, "encounterType");
        final String encounterTypeName = encounterType != null ? (String) PropertyUtils.getProperty(encounterType, "name") : null;
        atomFeedSpringTransactionManager.executeWithTransaction(
                new AFTransactionWorkWithoutResult() {
                    @Override
                    protected void doInTransaction() {
                        eventService.notify(event);
                        if (shouldAddToEncounterAdviceState(encounterTypeName)) {
                            encounterAdviceState.addProcessedEncounter((String) encounterUuid);
                        }
                    }

                    @Override
                    public PropagationDefinition getTxPropagationDefinition() {
                        return PropagationDefinition.PROPAGATION_REQUIRED;
                    }
                }
        );
    }

    /*
    * We do not add encounter uuid to thread local when the encounter types are as follows.
    * These encounter types are getting created from OpenElis only.
    * Assumption : Encounters created through OpenElis are saved only once.
    * */
    private boolean shouldAddToEncounterAdviceState(String encounterTypeName) {
        return !(StringUtils.equals(encounterTypeName, "VALIDATION NOTES") ||
                StringUtils.equals(encounterTypeName, "LAB_RESULT") ||
                StringUtils.equals(encounterTypeName, "INVESTIGATION"));
    }

    private AtomFeedSpringTransactionManager findTransactionManager() {
        PlatformTransactionManager platformTransactionManager = getSpringPlatformTransactionManager();
        return new AtomFeedSpringTransactionManager(platformTransactionManager);
    }

    private EventServiceImpl getEventService(AtomFeedSpringTransactionManager atomFeedSpringTransactionManager) {
        AllEventRecordsQueueJdbcImpl records = new AllEventRecordsQueueJdbcImpl(atomFeedSpringTransactionManager);
        return new EventServiceImpl(records);
    }

    private PlatformTransactionManager getSpringPlatformTransactionManager() {
        List<PlatformTransactionManager> platformTransactionManagers = Context.getRegisteredComponents(PlatformTransactionManager.class);
        return platformTransactionManagers.get(0);
    }
}
