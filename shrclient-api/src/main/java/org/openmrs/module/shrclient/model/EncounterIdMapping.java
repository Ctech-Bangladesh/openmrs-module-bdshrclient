package org.openmrs.module.shrclient.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;

public class EncounterIdMapping extends IdMapping {
    private String healthId;

    public EncounterIdMapping(String internalId, String externalId, String uri, Date createdAt, Date lastSyncDateTime, Date serverUpdateDateTime) {
        super(internalId, externalId, IdMappingType.ENCOUNTER, uri, createdAt, lastSyncDateTime, serverUpdateDateTime);
        this.healthId = StringUtils.substringBefore(StringUtils.substringAfter(uri, "patients/"), "/encounters");
    }

    public EncounterIdMapping(String internalId, String externalId, String uri, Date lastSyncDateTime) {
        this(internalId, externalId, uri, new Date(), lastSyncDateTime, null);
    }

    public String getHealthId() {
        return this.healthId;
    }
}
