package org.openmrs.module.shrclient.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.fhir.utils.DateUtil;

import java.text.ParseException;
import java.util.Date;

import static org.openmrs.module.fhir.utils.DateUtil.SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER;
import static org.openmrs.module.fhir.utils.DateUtil.toDateString;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.DEFAULT, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class HealthIdCard {
    private String givenName;
    private String familyName;
    private String givenNameLocal;
    private String familyNameLocal;
    private String gender;
    private Date dob;
    private HIDCardAddress address;
    private String hid;
    private Date issuedDate;

    public HealthIdCard() {
    }

    @JsonProperty("englishName")
    public String getEnglishName() {
        return givenName + " " + familyName;
    }
    @JsonProperty("banglaName")
    public String getBanglaName() {
        if (StringUtils.isNotBlank(givenNameLocal) && StringUtils.isNotBlank(familyNameLocal))
            return givenNameLocal + " " + familyNameLocal;
        if (StringUtils.isNotBlank(givenNameLocal) ) return givenNameLocal;
        if (StringUtils.isNotBlank(familyNameLocal) ) return familyNameLocal;
        return "";
    }

    @JsonProperty("gender")
    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    @JsonProperty("dob")
    public String getDob() {
        return toDateString(dob, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER);
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }

    @JsonProperty("address")
    public String getAddress() {
        return address.getAsString();
    }

    @JsonProperty("hid")
    public String getHid() {
        return hid;
    }

    public void setHid(String hid) {
        this.hid = hid;
    }

    @JsonProperty("issuedDate")
    public String getIssuedDate() {
        return toDateString(issuedDate, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER);
    }

    @JsonIgnore
    public void setIssuedDate(Date issuedDate) {
        this.issuedDate = issuedDate;
    }


    public HIDCardAddress addAddress() {
        address = new HIDCardAddress();
        return address;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public void setGivenNameLocal(String givenNameLocal) {
        this.givenNameLocal = givenNameLocal;
    }

    public void setFamilyNameLocal(String familyNameLocal) {
        this.familyNameLocal = familyNameLocal;
    }

    public class HIDCardAddress {
        private String address1;
        private String address2;
        private String address3;
        private String address4;
        private String address5;
        private String countyDistrict;
        private String stateProvince;

        public String getAddress1() {
            return address1;
        }

        public void setAddress1(String address1) {
            this.address1 = address1;
        }

        public String getAddress2() {
            return address2;
        }

        public void setAddress2(String address2) {
            this.address2 = address2;
        }

        public String getAddress3() {
            return address3;
        }

        public void setAddress3(String address3) {
            this.address3 = address3;
        }

        public String getAddress4() {
            return address4;
        }

        public void setAddress4(String address4) {
            this.address4 = address4;
        }

        public String getAddress5() {
            return address5;
        }

        public void setAddress5(String address5) {
            this.address5 = address5;
        }

        public String getCountyDistrict() {
            return countyDistrict;
        }

        public void setCountyDistrict(String countyDistrict) {
            this.countyDistrict = countyDistrict;
        }

        public String getStateProvince() {
            return stateProvince;
        }

        public void setStateProvince(String stateProvince) {
            this.stateProvince = stateProvince;
        }

        public String getAsString() {
            String delimiter = ", ";
            StringBuilder builder = new StringBuilder(address1);
            builder.append(delimiter);
            if (StringUtils.isNotBlank(address2)) {
                builder.append(address2).append(delimiter);
            }
            if (StringUtils.isNotBlank(address3)) {
                builder.append(address3).append(delimiter);
            }
            if (StringUtils.isNotBlank(address4)) {
                builder.append(address4).append(delimiter);
            }
            if (StringUtils.isNotBlank(address5)) {
                builder.append(address5).append(delimiter);
            }
            return builder.append(countyDistrict).append(delimiter).append(stateProvince).toString();
        }
    }
}
