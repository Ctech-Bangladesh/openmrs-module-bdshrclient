package org.openmrs.module.shrclient.mapper;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.shrclient.model.PhoneNumber;

public class PhoneNumberMapper {

    public static PhoneNumber map(String phoneNumber) {
        PhoneNumber mciPhoneNumber = new PhoneNumber();
        if (StringUtils.isBlank(phoneNumber)) {
            return mciPhoneNumber;
        }
        phoneNumber = phoneNumber.trim();
        mciPhoneNumber.setNumber(phoneNumber);
        return mciPhoneNumber;
    }

    public static String map(PhoneNumber phoneNumber) {
        if (null == phoneNumber) {
            return StringUtils.EMPTY;
        }
        String openmrsPhoneNumber = "";
        if (StringUtils.isNotBlank(phoneNumber.getCountryCode())) {
            openmrsPhoneNumber = openmrsPhoneNumber + phoneNumber.getCountryCode();
        }
        if (StringUtils.isNotBlank(phoneNumber.getAreaCode())) {
            openmrsPhoneNumber = openmrsPhoneNumber + phoneNumber.getAreaCode();
        }
        if (StringUtils.isNotBlank(phoneNumber.getNumber())) {
            openmrsPhoneNumber = openmrsPhoneNumber + phoneNumber.getNumber();
        }
        if (StringUtils.isNotBlank(phoneNumber.getExtension())) {
            openmrsPhoneNumber = openmrsPhoneNumber + phoneNumber.getExtension();
        }
        return openmrsPhoneNumber.trim();
    }
}
