package org.openmrs.module.shrclient.model;

import java.util.Date;

public class OrderIdMapping extends IdMapping {

    public OrderIdMapping(String internalId, String externalId, String type, String uri, Date createdAt) {
        super(internalId, externalId, type, uri, createdAt);
    }

    public OrderIdMapping(String internalId, String externalId, String type, String uri) {
        this(internalId, externalId, type, uri, new Date());
    }
}
