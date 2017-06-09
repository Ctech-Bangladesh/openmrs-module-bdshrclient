package org.openmrs.module.shrclient.model;

import org.openmrs.module.fhir.OpenMRSConstants;

import java.util.Date;

import static org.openmrs.module.fhir.utils.DateUtil.SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER;
import static org.openmrs.module.fhir.utils.DateUtil.toDateString;

public class HealthIdCard {
    private String givenName;
    private String familyName;
    private String givenNameLocal;
    private String familyNameLocal;
    private String gender;
    private Date dob;
    private HIDCardAddress address;
    private String hid;
    private String nid;
    private Date issuedDate;
    private String brn;

    public HealthIdCard() {
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        if (familyName.equalsIgnoreCase(OpenMRSConstants.DEFAULT_LAST_NAME_CONSTANT))
            return "";
        return familyName;
    }

    public String getGivenNameLocal() {
        return givenNameLocal;
    }

    public String getFamilyNameLocal() {
        return familyNameLocal;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDob() {
        return toDateString(dob, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER);
    }

    public void setDob(Date dob) {
        this.dob = dob;
    }

    public HIDCardAddress getAddress() {
        return address;
    }

    public String getHid() {
        return hid;
    }

    public void setHid(String hid) {
        this.hid = hid;
    }

    public String getIssuedDate() {
        return toDateString(issuedDate, SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER);
    }

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

    public String getNid() {
        return nid;
    }

    public void setNid(String nid) {
        this.nid = nid;
    }

    public String getBrn() {
        return brn;
    }

    public void setBrn(String brn) {
        this.brn = brn;
    }

    public class HIDCardAddress {
        private String address1;
        private String address2;
        private String address3;
        private String address4;
        private String address5;
        private String countyDistrict;
        private String stateProvince;

        public HIDCardAddress() {
        }

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
    }
}
