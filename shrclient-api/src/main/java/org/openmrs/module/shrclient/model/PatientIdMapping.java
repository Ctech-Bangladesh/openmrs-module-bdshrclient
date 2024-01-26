package org.openmrs.module.shrclient.model;

import java.util.Date;

public class PatientIdMapping extends IdMapping {

    public PatientIdMapping(String internalId, String externalId, String uri, Date createdAt, Date lastSyncDateTime, Date serverUpdateDateTime) {
        super(internalId, externalId, IdMappingType.PATIENT, uri, createdAt, lastSyncDateTime, serverUpdateDateTime);
    }

    public PatientIdMapping(String internalId, String externalId, String uri, Date lastSyncDateTime) {
        this(internalId, externalId, uri, new Date(), lastSyncDateTime, null);
    }

    public PatientIdMapping(String internalId, String externalId, String uri) {
        this(internalId, externalId, uri, new Date(), null, null);
    }
}
