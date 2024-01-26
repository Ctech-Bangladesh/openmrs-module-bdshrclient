package org.openmrs.module.shrclient.model;

import java.util.Date;

public class ProviderIdMapping extends IdMapping {

    public ProviderIdMapping(String internalId, String externalId, String uri) {
        super(internalId, externalId, IdMappingType.PROVIDER, uri, new Date());
    }
}
