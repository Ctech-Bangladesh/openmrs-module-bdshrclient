package org.openmrs.module.shrclient.model.mci.api;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class MciPatientUpdateResponse {

    @JsonProperty("error_code")
    @JsonInclude(NON_EMPTY)
    private int errorCode;

    @JsonProperty("http_status")
    @JsonInclude(NON_EMPTY)
    private int httpStatus;

    @JsonProperty("message")
    @JsonInclude(NON_EMPTY)
    private String message;

    @JsonProperty("errors")
    @JsonInclude(NON_EMPTY)
    private MciPatientUpdateError[] errors;

    @JsonProperty("id")
    @JsonInclude(NON_EMPTY)
    private String healthId;


    public MciPatientUpdateResponse() {

    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MciPatientUpdateError[] getErrors() {
        return errors;
    }

    public void setErrors(MciPatientUpdateError[] errors) {
        this.errors = errors;
    }

    public String getHealthId() {
        return healthId;
    }

    public void setHealthId(String healthId) {
        this.healthId = healthId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MciPatientUpdateResponse)) return false;

        MciPatientUpdateResponse that = (MciPatientUpdateResponse) o;

        if (errorCode != that.errorCode) return false;
        if (httpStatus != that.httpStatus) return false;
        if (!Arrays.equals(errors, that.errors)) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (healthId != null ? !healthId.equals(that.healthId) : that.healthId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = errorCode;
        result = 31 * result + httpStatus;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (errors != null ? Arrays.hashCode(errors) : 0);
        result = 31 * result + (healthId != null ? healthId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MciPatientUpdateResponse{" +
                "errorCode=" + errorCode +
                ", httpStatus=" + httpStatus +
                ", id=" + healthId +
                ", message='" + message + '\'' +
                ", errors=" + Arrays.toString(errors) +
                '}';
    }
}
