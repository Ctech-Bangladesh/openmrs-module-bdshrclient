package org.openmrs.module.shrclient.advice;

import org.ict4h.atomfeed.server.service.EventService;
import org.ict4h.atomfeed.transaction.AFTransactionWork;
import org.ict4h.atomfeed.transaction.AFTransactionWorkWithoutResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.api.EncounterService;
import org.openmrs.module.atomfeed.transaction.support.AtomFeedSpringTransactionManager;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.fhir.utils.OMRSLocationService;

import java.lang.reflect.Method;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ShrEncounterAdviceTest {

    @Mock
    private AtomFeedSpringTransactionManager mockAtomFeedSpringTransactionManager;
    @Mock
    private EventService mockEventService;
    @Mock
    private EncounterAdviceState mockEncounterAdviceState;
    @Mock
    private OMRSLocationService mockLocationService;

    private ArgumentCaptor<AFTransactionWorkWithoutResult> captor = ArgumentCaptor.forClass(AFTransactionWorkWithoutResult.class);

    private ShrEncounterAdvice encounterSaveInterceptor;

    private Encounter encounter;

    @Before
    public void setup() {
        initMocks(this);
        encounterSaveInterceptor = new ShrEncounterAdvice(mockAtomFeedSpringTransactionManager, mockEventService, mockEncounterAdviceState, mockLocationService);
        encounter = new Encounter();
        encounter.setUuid("uuid");
    }

    @Test
    public void shouldPublishUpdateEventToFeedAfterSaveEncounter() throws Throwable {
        Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
        Object[] objects = new Object[]{encounter};
        when(mockEncounterAdviceState.hasAlreadyProcessedEncounter("uuid")).thenReturn(false);

        encounterSaveInterceptor.afterReturning(encounter, method, objects, null);
        verify(mockAtomFeedSpringTransactionManager).executeWithTransaction(any(AFTransactionWorkWithoutResult.class));
    }

    @Test
    public void shouldSaveEventInTheSameTransactionAsTheTrigger() throws Throwable {
        Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
        Object[] objects = new Object[]{encounter};
        when(mockEncounterAdviceState.hasAlreadyProcessedEncounter("uuid")).thenReturn(false);

        encounterSaveInterceptor.afterReturning(encounter, method, objects, null);
        verify(mockAtomFeedSpringTransactionManager).executeWithTransaction(captor.capture());

        assertEquals(AFTransactionWork.PropagationDefinition.PROPAGATION_REQUIRED, captor.getValue().getTxPropagationDefinition());
    }

    @Test
    public void shouldProcessEncounterIfNotAlreadyProcessed() throws Throwable {
        Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
        Object[] objects = new Object[]{encounter};
        when(mockEncounterAdviceState.hasAlreadyProcessedEncounter("uuid")).thenReturn(false);

        encounterSaveInterceptor.afterReturning(encounter, method, objects, null);
        verify(mockAtomFeedSpringTransactionManager).executeWithTransaction(captor.capture());
    }

    @Test
    public void shouldNotPublishEventIfEncounterLocationIsNotLoginLocation() throws Throwable {
        Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
        Object[] objects = new Object[]{encounter};
        Location location = new Location();
        encounter.setLocation(location);
        when(mockEncounterAdviceState.hasAlreadyProcessedEncounter("uuid")).thenReturn(false);

        encounterSaveInterceptor.afterReturning(encounter, method, objects, null);
        verify(mockAtomFeedSpringTransactionManager, never()).executeWithTransaction(any(AFTransactionWorkWithoutResult.class));
    }
}