package org.openmrs.module.shrclient.model;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Relation {

    @JsonProperty("type")
    @JsonInclude(NON_EMPTY)
    private String type;

    @JsonProperty("given_name")
    @JsonInclude(NON_EMPTY)
    private String givenName;

    @JsonProperty("sur_name")
    @JsonInclude(NON_EMPTY)
    private String surName;

    @JsonProperty("nid")
    @JsonInclude(NON_EMPTY)
    private String nid;

    @JsonProperty("hid")
    @JsonInclude(NON_EMPTY)
    private String hid;

    @JsonProperty("id")
    @JsonInclude(NON_EMPTY)
    private String id;

    public Relation() {

    }

    public Relation(String type, String givenName, String surName, String nid, String hid, String id) {
        this.type = type;
        this.givenName = givenName;
        this.surName = surName;
        this.nid = nid;
        this.hid = hid;
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getSurName() {
        return surName;
    }

    public void setSurName(String surName) {
        this.surName = surName;
    }

    public String getNid() {
        return nid;
    }

    public void setNid(String nid) {
        this.nid = nid;
    }

    public String getHid() {
        return hid;
    }

    public void setHid(String hid) {
        this.hid = hid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relation)) return false;

        Relation relation = (Relation) o;

        if (givenName != null ? !givenName.equals(relation.givenName) : relation.givenName != null) return false;
        if (hid != null ? !hid.equals(relation.hid) : relation.hid != null) return false;
        if (id != null ? !id.equals(relation.id) : relation.id != null) return false;
        if (nid != null ? !nid.equals(relation.nid) : relation.nid != null) return false;
        if (surName != null ? !surName.equals(relation.surName) : relation.surName != null) return false;
        if (type != null ? !type.equals(relation.type) : relation.type != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (givenName != null ? givenName.hashCode() : 0);
        result = 31 * result + (surName != null ? surName.hashCode() : 0);
        result = 31 * result + (nid != null ? nid.hashCode() : 0);
        result = 31 * result + (hid != null ? hid.hashCode() : 0);
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Relation{" +
                "type='" + type + '\'' +
                ", givenName='" + givenName + '\'' +
                ", surName='" + surName + '\'' +
                ", nid='" + nid + '\'' +
                ", hid='" + hid + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
