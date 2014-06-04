package org.openmrs.module.bdshrclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class Patient {

    @JsonProperty("nid")
    @JsonInclude(NON_EMPTY)
    private String nationalId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("middle_name")
    @JsonInclude(NON_EMPTY)
    private String middleName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("present_address")
    private Address address;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("hid")
    private String healthId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Patient patient = (Patient) o;

        if (!address.equals(patient.address)) return false;
        if (!firstName.equals(patient.firstName)) return false;
        if (!gender.equals(patient.gender)) return false;
        if (!lastName.equals(patient.lastName)) return false;
        if (middleName != null ? !middleName.equals(patient.middleName) : patient.middleName != null) return false;
        if (nationalId != null ? !nationalId.equals(patient.nationalId) : patient.nationalId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = nationalId != null ? nationalId.hashCode() : 0;
        result = 31 * result + firstName.hashCode();
        result = 31 * result + (middleName != null ? middleName.hashCode() : 0);
        result = 31 * result + lastName.hashCode();
        result = 31 * result + address.hashCode();
        result = 31 * result + gender.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Patient{");
        sb.append("nationalId='").append(nationalId).append('\'');
        sb.append(", healthId='").append(healthId).append('\'');
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", middleName='").append(middleName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", address=").append(address);
        sb.append(", gender='").append(gender).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getHealthId() {
        return healthId;
    }

    public void setHealthId(String healthId) {
        this.healthId = healthId;
    }
}

