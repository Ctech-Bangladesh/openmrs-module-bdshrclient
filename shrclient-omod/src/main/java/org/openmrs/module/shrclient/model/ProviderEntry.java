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
    @JsonProperty("properties")
    private Properties properties;
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

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public class Properties {

        @JsonProperty("maritalStatus")
        private String maritalStatus;

        @JsonProperty("freedomFighter")
        private String freedomFighter;

        @JsonProperty("tribial")
        private String tribial;

        @JsonProperty("qualification")
        private String qualification;

        @JsonProperty("category")
        private String category;

        @JsonProperty("discipline")
        private String discipline;

        @JsonProperty("department")
        private String department;

        @JsonProperty("attendanceid")
        private String attendanceid;

        @JsonProperty("professionalDiscipline")
        private String professionalDiscipline;

        @JsonProperty("designation")
        private String designation;

        @JsonProperty("designationBdProfessionalCategory")
        private String designationBdProfessionalCategory;

        @JsonProperty("designationDiscipline")
        private String designationDiscipline;

        @JsonProperty("machineid")
        private String[] machineid;

        // Constructors (default and parameterized) can also be added

        // Setters and Getters

        public String getMaritalStatus() {
            return maritalStatus;
        }

        public void setMaritalStatus(String maritalStatus) {
            this.maritalStatus = maritalStatus;
        }

        public String getFreedomFighter() {
            return freedomFighter;
        }

        public void setFreedomFighter(String freedomFighter) {
            this.freedomFighter = freedomFighter;
        }

        public String getTribial() {
            return tribial;
        }

        public void setTribial(String tribial) {
            this.tribial = tribial;
        }

        public String getQualification() {
            return qualification;
        }

        public void setQualification(String qualification) {
            this.qualification = qualification;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDiscipline() {
            return discipline;
        }

        public void setDiscipline(String discipline) {
            this.discipline = discipline;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getAttendanceid() {
            return attendanceid;
        }

        public void setAttendanceid(String attendanceid) {
            this.attendanceid = attendanceid;
        }

        public String getProfessionalDiscipline() {
            return professionalDiscipline;
        }

        public void setProfessionalDiscipline(String professionalDiscipline) {
            this.professionalDiscipline = professionalDiscipline;
        }

        public String getDesignation() {
            return designation;
        }

        public void setDesignation(String designation) {
            this.designation = designation;
        }

        public String getDesignationBdProfessionalCategory() {
            return designationBdProfessionalCategory;
        }

        public void setDesignationBdProfessionalCategory(String designationBdProfessionalCategory) {
            this.designationBdProfessionalCategory = designationBdProfessionalCategory;
        }

        public String getDesignationDiscipline() {
            return designationDiscipline;
        }

        public void setDesignationDiscipline(String designationDiscipline) {
            this.designationDiscipline = designationDiscipline;
        }

        public String[] getMachineid() {
            return machineid;
        }

        public void setMachineid(String[] machineid) {
            this.machineid = machineid;
        }
    }

}
