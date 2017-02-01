package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhoneNumber {

    @JsonProperty("country_code")
    @JsonInclude(NON_EMPTY)
    private String countryCode;

    @JsonProperty("area_code")
    @JsonInclude(NON_EMPTY)
    private String areaCode;

    @JsonProperty("number")
    @JsonInclude(NON_EMPTY)
    private String number;

    @JsonProperty("extension")
    @JsonInclude(NON_EMPTY)
    private String extension;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhoneNumber)) return false;

        PhoneNumber that = (PhoneNumber) o;

        if (areaCode != null ? !areaCode.equals(that.areaCode) : that.areaCode != null) return false;
        if (countryCode != null ? !countryCode.equals(that.countryCode) : that.countryCode != null) return false;
        if (extension != null ? !extension.equals(that.extension) : that.extension != null) return false;
        if (number != null ? !number.equals(that.number) : that.number != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = countryCode != null ? countryCode.hashCode() : 0;
        result = 31 * result + (areaCode != null ? areaCode.hashCode() : 0);
        result = 31 * result + number != null ? number.hashCode() : 0;
        result = 31 * result + (extension != null ? extension.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PhoneNumber{");
        sb.append("areaCode='").append(areaCode).append('\'');
        sb.append(", countryCode='").append(countryCode).append('\'');
        sb.append(", number='").append(number).append('\'');
        sb.append(", extension='").append(extension).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
