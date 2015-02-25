package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class Patient {

    @JsonProperty("nid")
    @JsonInclude(NON_EMPTY)
    private String nationalId;

    @JsonProperty("hid")
    @JsonInclude(NON_EMPTY)
    private String healthId;

    @JsonProperty("given_name")
    private String givenName;

    @JsonProperty("sur_name")
    private String surName;

    @JsonProperty("date_of_birth")
    private String dateOfBirth;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("occupation")
    @JsonInclude(NON_EMPTY)
    private String occupation;

    @JsonProperty("edu_level")
    @JsonInclude(NON_EMPTY)
    private String educationLevel;

    @JsonProperty("primary_contact")
    @JsonInclude(NON_EMPTY)
    private String primaryContact;

    @JsonProperty("present_address")
    private Address address;

    @JsonProperty("bin_brn")
    @JsonInclude(NON_EMPTY)
    private String birthRegNumber;

    @JsonProperty("uid")
    @JsonInclude(NON_EMPTY)
    private String uniqueId;

    @JsonProperty("relations")
    @JsonInclude(NON_NULL)
    private Relation[] relations;

    @JsonProperty("status")
    private Character status;

    @JsonProperty("date_of_death")
    private String dateOfDeath;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient)) return false;

        Patient patient = (Patient) o;

        if (address != null ? !address.equals(patient.address) : patient.address != null) return false;
        if (dateOfBirth != null ? !dateOfBirth.equals(patient.dateOfBirth) : patient.dateOfBirth != null) return false;
        if (educationLevel != null ? !educationLevel.equals(patient.educationLevel) : patient.educationLevel != null)
            return false;
        if (givenName != null ? !givenName.equals(patient.givenName) : patient.givenName != null) return false;
        if (gender != null ? !gender.equals(patient.gender) : patient.gender != null) return false;
        if (healthId != null ? !healthId.equals(patient.healthId) : patient.healthId != null) return false;
        if (surName != null ? !surName.equals(patient.surName) : patient.surName != null) return false;
        if (nationalId != null ? !nationalId.equals(patient.nationalId) : patient.nationalId != null) return false;
        if (occupation != null ? !occupation.equals(patient.occupation) : patient.occupation != null) return false;
        if (primaryContact != null ? !primaryContact.equals(patient.primaryContact) : patient.primaryContact != null)
            return false;
        if (birthRegNumber != null ? !birthRegNumber.equals(patient.birthRegNumber) : patient.birthRegNumber != null)
            return false;
        if (uniqueId != null ? !uniqueId.equals(patient.uniqueId) : patient.uniqueId != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = nationalId != null ? nationalId.hashCode() : 0;
        result = 31 * result + (healthId != null ? healthId.hashCode() : 0);
        result = 31 * result + (givenName != null ? givenName.hashCode() : 0);
        result = 31 * result + (surName != null ? surName.hashCode() : 0);
        result = 31 * result + (dateOfBirth != null ? dateOfBirth.hashCode() : 0);
        result = 31 * result + (gender != null ? gender.hashCode() : 0);
        result = 31 * result + (occupation != null ? occupation.hashCode() : 0);
        result = 31 * result + (educationLevel != null ? educationLevel.hashCode() : 0);
        result = 31 * result + (primaryContact != null ? primaryContact.hashCode() : 0);
        result = 31 * result + (address != null ? address.hashCode() : 0);
        result = 31 * result + (birthRegNumber != null ? birthRegNumber.hashCode() : 0);
        result = 31 * result + (uniqueId != null ? uniqueId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Patient{");
        sb.append("nationalId='").append(nationalId).append('\'');
        sb.append(", healthId='").append(healthId).append('\'');
        sb.append(", givenName='").append(givenName).append('\'');
        sb.append(", surName='").append(surName).append('\'');
        sb.append(", dateOfBirth='").append(dateOfBirth).append('\'');
        sb.append(", address=").append(address);
        sb.append(", gender='").append(gender).append('\'');
        sb.append(", occupation='").append(occupation).append('\'');
        sb.append(", educationLevel='").append(educationLevel).append('\'');
        sb.append(", primaryContact='").append(primaryContact).append('\'');
        sb.append(", birthRegNumber='").append(birthRegNumber).append('\'');
        sb.append(", uniqueId='").append(uniqueId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getHealthId() {
        return healthId;
    }

    public void setHealthId(String healthId) {
        this.healthId = healthId;
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

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(String educationLevel) {
        this.educationLevel = educationLevel;
    }

    public String getPrimaryContact() {
        return primaryContact;
    }

    public void setPrimaryContact(String primaryContact) {
        this.primaryContact = primaryContact;
    }

    public String getBirthRegNumber() {
        return birthRegNumber;
    }

    public void setBirthRegNumber(String birthRegNumber) {
        this.birthRegNumber = birthRegNumber;
    }

    public Relation[] getRelations() {
        return relations;
    }

    public void setRelations(Relation[] relations) {
        this.relations = relations;
    }

    public String getDateOfDeath() {
        return dateOfDeath;
    }

    public void setDateOfDeath(String dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
    }

    public Character getStatus() {
        return status;
    }

    public void setStatus(char status) {
        this.status = status;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }
}

