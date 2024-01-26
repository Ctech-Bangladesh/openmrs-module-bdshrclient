package org.openmrs.module.shrclient.web.controller;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class MciPatientSearchRequest {
    private String nid;
    private String hid;

    private String uid;
    private String brn;
    private String houseHoldCode;

    private String phoneNo;

    private String inactiveHids;

    public String getNid() {
        return nid;
    }

    public void setNid(String nid) {
        this.nid = nid;
    }

    public String getHid() {
        return hid;
    }

    public void setHid(String hid) {
        this.hid = hid;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getBrn() {
        return brn;
    }

    public void setBrn(String brn) {
        this.brn = brn;
    }

    public String getHouseHoldCode() {
        return houseHoldCode;
    }

    public void setHouseHoldCode(String houseHoldCode) {
        this.houseHoldCode = houseHoldCode;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public List<String> getInactiveHids() {
        return inactiveHids != null ? Arrays.asList(StringUtils.split(inactiveHids, ",")) : null;
    }

    public void setInactiveHids(String inactiveHids) {
        this.inactiveHids = inactiveHids;
    }

    @Override
    public String toString() {
        return "MciPatientSearchRequest{" +
                "nid='" + nid + '\'' +
                ", hid='" + hid + '\'' +
                ", uid='" + uid + '\'' +
                ", brn='" + brn + '\'' +
                ", houseHoldCode='" + houseHoldCode + '\'' +
                ", phoneNo='" + phoneNo + '\'' +
                '}';
    }
}
