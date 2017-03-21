package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderEntry {
    @JsonProperty("id")
    private String id;
    @JsonProperty("url")
    private String url;
    @JsonProperty("name")
    private String name;
    @JsonProperty("organization")
    private Organization organization;
    @JsonProperty("active")
    private String active;
    @JsonProperty("gender")
    private String gender;
    @JsonProperty("birthDate")
    private String birthDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {return gender;}

    public void setGender(String gender) {this.gender = gender;}

    public String getBirthDate() {return birthDate;}

    public void setBirthDate(String birthDate) {this.birthDate = birthDate;}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Organization {
        @JsonProperty("reference")
        private String reference;
        @JsonProperty("display")
        private String display;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }
}
