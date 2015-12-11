package org.openmrs.module.fhir.mapper.model;

import ca.uhn.fhir.model.dstu2.resource.Bundle;

public class ShrEncounterComposition {
    private Bundle bundle;
    private String healthId;
    private String shrEncounterId;

    public ShrEncounterComposition(Bundle bundle, String healthId, String shrEncounterId) {
        this.bundle = bundle;
        this.healthId = healthId;
        this.shrEncounterId = shrEncounterId;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public String getHealthId() {
        return healthId;
    }

    public String getShrEncounterId() {
        return shrEncounterId;
    }
}
