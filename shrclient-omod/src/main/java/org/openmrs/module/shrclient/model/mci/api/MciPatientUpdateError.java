package org.openmrs.module.shrclient.model.mci.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class MciPatientUpdateError {

    @JsonProperty("code")
    @JsonInclude(NON_EMPTY)
    private int code;

    @JsonProperty("field")
    @JsonInclude(NON_EMPTY)
    private String field;

    @JsonProperty("message")
    @JsonInclude(NON_EMPTY)
    private String message;

    public MciPatientUpdateError () {

    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MciPatientUpdateError)) return false;

        MciPatientUpdateError that = (MciPatientUpdateError) o;

        if (code != that.code) return false;
        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MciPatientUpdateError{" +
                "code=" + code +
                ", field='" + field + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
