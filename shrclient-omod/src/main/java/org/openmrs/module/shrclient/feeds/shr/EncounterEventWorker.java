package org.openmrs.module.shrclient.feeds.shr;

import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

public interface EncounterEventWorker {
    void process(EncounterEvent encounterEvent);
}
