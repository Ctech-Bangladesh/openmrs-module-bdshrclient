package org.openmrs.module.shrclient.feeds.shr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import org.ict4h.atomfeed.client.domain.Event;
import org.ict4h.atomfeed.client.service.EventWorker;
import org.openmrs.module.shrclient.util.FhirBundleContextHolder;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

public class ShrFeedEventWorker implements EventWorker {
    private EncounterEventWorker shrEventWorker;

    public ShrFeedEventWorker(EncounterEventWorker shrEventWorker) {
        this.shrEventWorker = shrEventWorker;
    }

    @Override
    public void process(Event event) {
        String content = event.getContent();
        FhirContext fhirContext = FhirBundleContextHolder.getFhirContext();
        Bundle bundle;
        try {
            bundle = fhirContext.newXmlParser().parseResource(Bundle.class, content);
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse XML", e);
        }
        EncounterEvent encounterEvent = new EncounterEvent();
        encounterEvent.setTitle(event.getTitle());
        encounterEvent.addContent(bundle);
        encounterEvent.setCategories(event.getCategories());
        shrEventWorker.process(encounterEvent);
    }

    @Override
    public void cleanUp(Event event) {
    }
}
