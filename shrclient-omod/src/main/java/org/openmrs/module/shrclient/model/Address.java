package org.openmrs.module.shrclient.model;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address {
    @JsonProperty("address_line")
    private String addressLine;

    @JsonProperty("division_id")
    private String divisionId;

    @JsonProperty("district_id")
    private String districtId;

    @JsonProperty("upazila_id")
    private String upazilaId;

    @JsonProperty("city_corporation_id")
    private String cityCorporationId;

    @JsonProperty("union_or_urban_ward_id")
    private String unionOrUrbanWardId;

    @JsonProperty("rural_ward_id")
    @JsonInclude(NON_EMPTY)
    private String ruralWardId;

    @JsonProperty("thana_id")
    @JsonInclude(NON_EMPTY)
    private String thanaId;

    public Address() {
    }

    public Address(String addressLine, String divisionId, String districtId, String upazilaId, String cityCorporationId, String unionOrUrbanWardId, String ruralWardId, String thanaId) {
        this.addressLine = addressLine;
        this.divisionId = divisionId;
        this.districtId = districtId;
        this.upazilaId = upazilaId;
        this.cityCorporationId = cityCorporationId;
        this.unionOrUrbanWardId = unionOrUrbanWardId;
        this.ruralWardId = ruralWardId;
        this.thanaId = thanaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address)) return false;

        Address address = (Address) o;

        if (addressLine != null ? !addressLine.equals(address.addressLine) : address.addressLine != null) return false;
        if (divisionId != null ? !divisionId.equals(address.divisionId) : address.divisionId != null) return false;
        if (districtId != null ? !districtId.equals(address.districtId) : address.districtId != null) return false;
        if (upazilaId != null ? !upazilaId.equals(address.upazilaId) : address.upazilaId != null) return false;
        if (cityCorporationId != null ? !cityCorporationId.equals(address.cityCorporationId) : address.cityCorporationId != null)
            return false;
        if (unionOrUrbanWardId != null ? !unionOrUrbanWardId.equals(address.unionOrUrbanWardId) : address.unionOrUrbanWardId != null) return false;
        if (ruralWardId != null ? !ruralWardId.equals(address.ruralWardId) : address.ruralWardId != null) return false;
        if (thanaId != null ? !thanaId.equals(address.thanaId) : address.thanaId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = addressLine != null ? addressLine.hashCode() : 0;
        result = 31 * result + (divisionId != null ? divisionId.hashCode() : 0);
        result = 31 * result + (districtId != null ? districtId.hashCode() : 0);
        result = 31 * result + (upazilaId != null ? upazilaId.hashCode() : 0);
        result = 31 * result + (cityCorporationId != null ? cityCorporationId.hashCode() : 0);
        result = 31 * result + (unionOrUrbanWardId != null ? unionOrUrbanWardId.hashCode() : 0);
        result = 31 * result + (ruralWardId != null ? ruralWardId.hashCode() : 0);
        result = 31 * result + (thanaId != null ? thanaId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Address{");
        sb.append("addressLine='").append(addressLine).append('\'');
        sb.append(", divisionId='").append(divisionId).append('\'');
        sb.append(", districtId='").append(districtId).append('\'');
        sb.append(", upazilaId='").append(upazilaId).append('\'');
        sb.append(", cityCorporationId='").append(cityCorporationId).append('\'');
        sb.append(", unionOrUrbanWardId='").append(unionOrUrbanWardId).append('\'');
        sb.append(", ruralWardId='").append(ruralWardId).append('\'');
        sb.append(", thanaId='").append(thanaId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getDivisionId() {
        return divisionId;
    }

    public void setDivisionId(String divisionId) {
        this.divisionId = divisionId;
    }

    public String getDistrictId() {
        return districtId;
    }

    public void setDistrictId(String districtId) {
        this.districtId = districtId;
    }

    public String getUpazilaId() {
        return upazilaId;
    }

    public void setUpazilaId(String upazilaId) {
        this.upazilaId = upazilaId;
    }

    public String getCityCorporationId() {
        return cityCorporationId;
    }

    public void setCityCorporationId(String cityCorporationId) {
        this.cityCorporationId = cityCorporationId;
    }

    public String getUnionOrUrbanWardId() {
        return unionOrUrbanWardId;
    }

    public void setUnionOrUrbanWardId(String unionOrUrbanWardId) {
        this.unionOrUrbanWardId = unionOrUrbanWardId;
    }

    public String createUserGeneratedDistrictId() {
        return divisionId + districtId;
    }

    public String createUserGeneratedUpazillaId() {
        return divisionId + districtId + upazilaId;
    }

    public String createUserGeneratedCityCorporationId() {
        return divisionId + districtId + upazilaId + cityCorporationId;
    }

    public String createUserGeneratedUnionOrUrbanWardId() {
        return divisionId + districtId + upazilaId + cityCorporationId + unionOrUrbanWardId;
    }

    public String createUserGeneratedRuralWardId() {
        return divisionId + districtId + upazilaId + cityCorporationId + unionOrUrbanWardId + ruralWardId;
    }

    public String getRuralWardId() {
        return ruralWardId;
    }

    public void setRuralWardId(String ruralWardId) {
        this.ruralWardId = ruralWardId;
    }

    public String getThanaId() {
        return thanaId;
    }

    public void setThanaId(String thanaId) {
        this.thanaId = thanaId;
    }


    public static String getAddressCodeForLevel(String code, int level) {
        if (code == null)
            return null;

        if (code.length() < (level * 2)) {
            return ""; //fail instead?
        }

        int beginIndex = (level - 1) * 2;
        return code.substring(beginIndex, beginIndex + 2);
    }

    public String getAddressCode() {
        StringBuilder addressCode = new StringBuilder();

        appendAddressPart(addressCode, divisionId);
        appendAddressPart(addressCode, districtId);
        appendAddressPart(addressCode, upazilaId);
        appendAddressPart(addressCode, cityCorporationId);
        appendAddressPart(addressCode, unionOrUrbanWardId);
        appendAddressPart(addressCode, ruralWardId);
        return addressCode.toString();
    }

    private void appendAddressPart(StringBuilder addressCode, String addressPart) {
        if (isNotBlank(addressPart)) {
            addressCode.append(format("%s", addressPart));
        }
    }
}
