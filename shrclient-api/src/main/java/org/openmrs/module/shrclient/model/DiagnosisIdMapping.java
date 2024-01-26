package org.openmrs.module.shrclient.model;

import java.util.Date;

public class DiagnosisIdMapping extends IdMapping {

    public DiagnosisIdMapping(String internalId, String externalId, String uri, Date createdAt) {
        super(internalId, externalId, IdMappingType.DIAGNOSIS, uri, createdAt);
    }

    public DiagnosisIdMapping(String internalId, String externalId, String diagnosisUrl) {
        this(internalId,externalId,diagnosisUrl, new Date());
    }
}
