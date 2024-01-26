package org.openmrs.module.shrclient.model.mci.api;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openmrs.module.shrclient.model.Patient;

import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class MciPatientSearchResponse {
    @JsonProperty("http_status")
    @JsonInclude(NON_EMPTY)
    private int httpStatus;

    @JsonProperty("results")
    @JsonInclude(NON_EMPTY)
    private Patient[] results;


    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Patient[] getResults() {
        return results;
    }

    public void setResults(Patient[] results) {
        this.results = results;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MciPatientSearchResponse)) return false;

        MciPatientSearchResponse that = (MciPatientSearchResponse) o;

        if (httpStatus != that.httpStatus) return false;
        if (!Arrays.equals(results, that.results)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = httpStatus;
        result = 31 * result + (results != null ? Arrays.hashCode(results) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "MciPatientSearchResponse{" +
                "httpStatus=" + httpStatus +
                ", results=" + Arrays.toString(results) +
                '}';
    }
}
