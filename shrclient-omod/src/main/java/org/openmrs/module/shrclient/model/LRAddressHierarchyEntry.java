package org.openmrs.module.shrclient.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LRAddressHierarchyEntry {

    @JsonProperty("code")
    @JsonInclude(NON_EMPTY)
    private String shortLocationCode;

    @JsonProperty("id")
    @JsonInclude(NON_EMPTY)
    private String fullLocationCode;

    @JsonProperty("name")
    @JsonInclude(NON_EMPTY)
    private String locationName;

    @JsonProperty("type")
    @JsonInclude(NON_EMPTY)
    private String locationLevelName;

    @JsonProperty("active")
    @JsonInclude(NON_EMPTY)
    private String active;

    @JsonProperty("name_BN")
    @JsonInclude(NON_EMPTY)
    private String localName;

    public String getShortLocationCode() {
        return shortLocationCode;
    }

    public void setShortLocationCode(String shortLocationCode) {
        this.shortLocationCode = shortLocationCode;
    }

    public String getFullLocationCode() {
        return fullLocationCode;
    }

    public void setFullLocationCode(String fullLocationCode) {
        this.fullLocationCode = fullLocationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getLocationLevelName() {
        return locationLevelName;
    }

    public void setLocationLevelName(String locationLevelName) {
        this.locationLevelName = locationLevelName;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LRAddressHierarchyEntry{");
        sb.append("shortLocationCode='").append(shortLocationCode).append('\'');
        sb.append(", fullLocationCode='").append(fullLocationCode).append('\'');
        sb.append(", locationName='").append(locationName).append('\'');
        sb.append(", locationLevelName='").append(locationLevelName).append('\'');
        sb.append(", active='").append(active).append('\'');
        sb.append(", name_BN='").append(localName).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LRAddressHierarchyEntry that = (LRAddressHierarchyEntry) o;

        if (active != null ? !active.equals(that.active) : that.active != null) return false;
        if (!fullLocationCode.equals(that.fullLocationCode)) return false;
        if (!locationLevelName.equals(that.locationLevelName)) return false;
        if (!locationName.equals(that.locationName)) return false;
        if (!shortLocationCode.equals(that.shortLocationCode)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = shortLocationCode.hashCode();
        result = 31 * result + fullLocationCode.hashCode();
        result = 31 * result + locationName.hashCode();
        result = 31 * result + locationLevelName.hashCode();
        result = 31 * result + (active != null ? active.hashCode() : 0);
        return result;
    }

    public String getLocalName() {
        return localName;
    }

    public String setLocalName() {
        return localName;
    }
}
