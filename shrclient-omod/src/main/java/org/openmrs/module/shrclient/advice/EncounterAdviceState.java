package org.openmrs.module.shrclient.advice;

/**
 * This is a hack!
 * This is done so that multiple events is not created for the same encounter during the encounter processing.
 *  EmrEncounterService calls EncounterService multiple times during a transaction. Specifically while creating
 *  orders. This results in creation of multiple encounter events.
 *  because the SHREncounterAdvice intercepts EncounterService.save()
 */
public class EncounterAdviceState {
    private static ThreadLocal<String> processingEncounterIdThread;

    public void addProcessedEncounter(String encounterId) {
        if(processingEncounterIdThread == null)
            processingEncounterIdThread = new ThreadLocal<String>();
        processingEncounterIdThread.set(encounterId);
    }
    public boolean hasAlreadyProcessedEncounter(String encounterId) {
        if(processingEncounterIdThread == null) return false;
        String processsingEncounterId = processingEncounterIdThread.get();
        return (processsingEncounterId != null) && processsingEncounterId.trim().equals(encounterId) ? true : false;
    }

    public void reset() {
        processingEncounterIdThread.remove();
    }
}
