package org.openmrs.module.fhir.mapper.model;


import org.hl7.fhir.dstu3.model.Bundle;

public class ShrEncounterBundle {
    private Bundle bundle;
    private String healthId;
    private String shrEncounterId;

    public ShrEncounterBundle(Bundle bundle, String healthId, String shrEncounterId) {
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
