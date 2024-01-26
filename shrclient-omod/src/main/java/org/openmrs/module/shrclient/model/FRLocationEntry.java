package org.openmrs.module.shrclient.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FRLocationEntry {

    @JsonProperty("name")
    @JsonInclude(NON_EMPTY)
    private String name;

    @JsonProperty("url")
    private String url;

    @JsonProperty("id")
    @JsonInclude(NON_EMPTY)
    private String id;

    @JsonProperty("active")
    private String active;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("coordinates")
    private List<String> coordinates;

    @JsonProperty("identifiers")
    private Identifiers identifiers;

    @JsonProperty("properties")
    private Properties properties = new Properties();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<String> coordinates) {
        this.coordinates = coordinates;
    }

    public Identifiers getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(Identifiers identifiers) {
        this.identifiers = identifiers;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Identifiers {
        @JsonProperty("agency")
        private String agency;

        @JsonProperty("context")
        private String context;

        @JsonProperty("id")
        private String id;

        public String getAgency() {
            return agency;
        }

        public void setAgency(String agency) {
            this.agency = agency;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Properties {
        @JsonProperty("ownership")
        private String ownership;

        @JsonProperty("org_type")
        private String orgType;

        @JsonProperty("org_level")
        private String orgLevel;

        @JsonProperty("care_level")
        private String careLevel;

        @JsonProperty("services")
        private List<String> services;

        @JsonProperty("locations")
        private Locations locations;

        @JsonProperty("contacts")
        private Contacts contacts;

        @JsonProperty("catchment")
        private List<String> catchments;

        public String getOwnership() {
            return ownership;
        }

        public void setOwnership(String ownership) {
            this.ownership = ownership;
        }

        public String getOrgType() {
            return orgType;
        }

        public void setOrgType(String orgType) {
            this.orgType = orgType;
        }

        public String getOrgLevel() {
            return orgLevel;
        }

        public void setOrgLevel(String orgLevel) {
            this.orgLevel = orgLevel;
        }

        public String getCareLevel() {
            return careLevel;
        }

        public void setCareLevel(String careLevel) {
            this.careLevel = careLevel;
        }

        public List<String> getServices() {
            return services;
        }

        public void setServices(List<String> services) {
            this.services = services;
        }

        public Locations getLocations() {
            return locations;
        }

        public void setLocations(Locations locations) {
            this.locations = locations;
        }

        public Contacts getContacts() {
            return contacts;
        }

        public void setContacts(Contacts contacts) {
            this.contacts = contacts;
        }

        public List<String> getCatchments() {
            return catchments;
        }

        public void setCatchments(List<String> catchments) {
            this.catchments = catchments;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public class Locations {
            @JsonProperty("division_code")
            private String divisionCode;

            @JsonProperty("district_code")
            private String districtCode;

            @JsonProperty("upazila_code")
            private String upazilaCode;

            @JsonProperty("paurasava_code")
            private String paurasavaCode;

            @JsonProperty("union_code")
            private String unionCode;

            @JsonProperty("ward_code")
            private String wardCode;

            public String getDivisionCode() {
                return divisionCode;
            }

            public void setDivisionCode(String divisionCode) {
                this.divisionCode = divisionCode;
            }

            public String getDistrictCode() {
                return districtCode;
            }

            public void setDistrictCode(String districtCode) {
                this.districtCode = districtCode;
            }

            public String getUpazilaCode() {
                return upazilaCode;
            }

            public void setUpazilaCode(String upazilaCode) {
                this.upazilaCode = upazilaCode;
            }

            public String getPaurasavaCode() {
                return paurasavaCode;
            }

            public void setPaurasavaCode(String paurasavaCode) {
                this.paurasavaCode = paurasavaCode;
            }

            public String getUnionCode() {
                return unionCode;
            }

            public void setUnionCode(String unionCode) {
                this.unionCode = unionCode;
            }

            public String getWardCode() {
                return wardCode;
            }

            public void setWardCode(String wardCode) {
                this.wardCode = wardCode;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public class Contacts {
            @JsonProperty("name")
            private String name;

            @JsonProperty("email")
            private String email;

            @JsonProperty("phone")
            private String phone;

            @JsonProperty("mobile")
            private String mobile;

            @JsonProperty("fax")
            private String fax;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }

            public String getPhone() {
                return phone;
            }

            public void setPhone(String phone) {
                this.phone = phone;
            }

            public String getMobile() {
                return mobile;
            }

            public void setMobile(String mobile) {
                this.mobile = mobile;
            }

            public String getFax() {
                return fax;
            }

            public void setFax(String fax) {
                this.fax = fax;
            }
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FRLocationEntry that = (FRLocationEntry) o;

        if (active != null ? !active.equals(that.active) : that.active != null) return false;
        if (coordinates != null ? !coordinates.equals(that.coordinates) : that.coordinates != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (!id.equals(that.id)) return false;
        if (identifiers != null ? !identifiers.equals(that.identifiers) : that.identifiers != null) return false;
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) return false;
        if (!name.equals(that.name)) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + id.hashCode();
        result = 31 * result + (active != null ? active.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        result = 31 * result + (coordinates != null ? coordinates.hashCode() : 0);
        result = 31 * result + (identifiers != null ? identifiers.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FRLocationEntry{");
        sb.append("name='").append(name).append('\'');
        sb.append(", url='").append(url).append('\'');
        sb.append(", id='").append(id).append('\'');
        sb.append(", active='").append(active).append('\'');
        sb.append(", createdAt='").append(createdAt).append('\'');
        sb.append(", updatedAt='").append(updatedAt).append('\'');
        sb.append(", coordinates=").append(coordinates);
        sb.append(", identifiers=").append(identifiers);
        sb.append(", properties=").append(properties);
        sb.append('}');
        return sb.toString();
    }
}
