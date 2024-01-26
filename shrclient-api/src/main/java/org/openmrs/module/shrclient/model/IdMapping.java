package org.openmrs.module.shrclient.model;

import org.apache.commons.lang.Validate;

import java.sql.Timestamp;
import java.util.Date;

public class IdMapping {

    private long id;
    private String internalId;
    private String externalId;
    private String type;
    private String uri;
    private Date createdAt;
    private Date lastSyncDateTime;
    private Date serverUpdateDateTime;

    public IdMapping(String internalId, String externalId, String type, String uri, Date createdAt, Date lastSyncDateTime, Date serverUpdateDateTime) {
        Validate.notNull(internalId);
        Validate.notNull(externalId);
        Validate.notNull(type);
        this.internalId = internalId;
        this.externalId = externalId;
        this.type = type;
        this.uri = uri;
        this.createdAt = createdAt;
        this.lastSyncDateTime = lastSyncDateTime;
        this.serverUpdateDateTime = serverUpdateDateTime;
    }

    public IdMapping(String internalId, String externalId, String type, String uri, Date createdAt) {
        this(internalId, externalId, type, uri, createdAt, null, null);
    }

    public IdMapping() {
    }

    public String getInternalId() {
        return internalId;
    }

    public String getExternalId() {
        return externalId;
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getUri() {
        return uri;
    }

    public Date getLastSyncDateTime() {
        return lastSyncDateTime;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setInternalId(String internalId) {
        this.internalId = internalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setLastSyncDateTime(Date lastSyncDateTime) {
        this.lastSyncDateTime = lastSyncDateTime;
    }

    public Timestamp getLastSyncTimestamp() {
        return this.lastSyncDateTime != null ? new Timestamp(lastSyncDateTime.getTime()) : new Timestamp(new Date().getTime());
    }

    public Timestamp getServerUpdateTimestamp() {
        return this.serverUpdateDateTime != null ? new Timestamp(this.serverUpdateDateTime.getTime()) : null;
    }

    public Date getServerUpdateDateTime() {
        return serverUpdateDateTime;
    }

    public void setServerUpdateDateTime(Date serverUpdateDateTime) {
        this.serverUpdateDateTime = serverUpdateDateTime;
    }

    public Timestamp getCreatedAt() {
        return this.createdAt != null ? new Timestamp(this.createdAt.getTime()) : null;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdMapping)) return false;

        IdMapping idMapping = (IdMapping) o;

        if (!externalId.equals(idMapping.externalId)) return false;
        if (!internalId.equals(idMapping.internalId)) return false;
        if (!type.equals(idMapping.type)) return false;
        if (uri != null ? !uri.equals(idMapping.uri) : idMapping.uri != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = internalId.hashCode();
        result = 31 * result + externalId.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
        return result;
    }
}
