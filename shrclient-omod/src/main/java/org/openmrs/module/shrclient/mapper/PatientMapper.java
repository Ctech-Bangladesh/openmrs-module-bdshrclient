package org.openmrs.module.shrclient.mapper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.PhoneNumber;
import org.openmrs.module.shrclient.model.Relation;
import org.openmrs.module.shrclient.model.Status;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.util.AddressHelper;

import java.util.Date;
import java.util.List;

import static org.openmrs.module.fhir.OpenMRSConstants.*;
import static org.openmrs.module.fhir.utils.MCIConstants.*;

public class PatientMapper {
    private BbsCodeService bbsCodeService;
    private IdMappingRepository idMappingsRepository;
    private AddressHelper addressHelper;
    private PersonAttributeMapper personAttributeMapper;

    public PatientMapper(BbsCodeService bbsCodeService, IdMappingRepository idMappingRepository) {
        this.bbsCodeService = bbsCodeService;
        this.idMappingsRepository = idMappingRepository;
        this.addressHelper = new AddressHelper();
        this.personAttributeMapper = new PersonAttributeMapper();
    }

    public PatientMapper(BbsCodeService bbsCodeService, AddressHelper addressHelper, IdMappingRepository idMappingRepository) {
        this.bbsCodeService = bbsCodeService;
        this.addressHelper = addressHelper;
        this.idMappingsRepository = idMappingRepository;
        this.personAttributeMapper = new PersonAttributeMapper();
    }

    public Patient map(org.openmrs.Patient openMrsPatient) {
        Patient patient = new Patient();

        String givenNameLocal = getAttributeValue(openMrsPatient, GIVEN_NAME_LOCAL);
        String familyNameLocal = getAttributeValue(openMrsPatient, FAMILY_NAME_LOCAL);
        String banglaName = (StringUtils.isNotBlank(givenNameLocal) ? givenNameLocal : "")
                .concat(" ")
                .concat((StringUtils.isNotBlank(familyNameLocal) ? familyNameLocal : "")).trim();

        patient.setGivenName(openMrsPatient.getGivenName());
        patient.setSurName(openMrsPatient.getFamilyName());
        patient.setGender(openMrsPatient.getGender());
        Date birthDateTime = openMrsPatient.getBirthDateTime() != null ? openMrsPatient.getBirthDateTime() : openMrsPatient.getBirthdate();
        patient.setDateOfBirth(birthDateTime);

        PatientIdentifier hid = openMrsPatient.getPatientIdentifier(HEALTH_ID_IDENTIFIER_TYPE_NAME);
        if (hid != null) {
            patient.setHealthId(hid.getIdentifier());
        }

        if (StringUtils.isNotBlank(banglaName)) {
            patient.setBanglaName(banglaName);
        }

        String nationalId = getAttributeValue(openMrsPatient, NATIONAL_ID_ATTRIBUTE);
        if (nationalId != null) {
            patient.setNationalId(nationalId);
        }

        String birthRegNo = getAttributeValue(openMrsPatient, BIRTH_REG_NO_ATTRIBUTE);
        if (birthRegNo != null) {
            patient.setBirthRegNumber(birthRegNo);
        }

        String houseHoldCode = getAttributeValue(openMrsPatient, HOUSE_HOLD_CODE_ATTRIBUTE);
        if (houseHoldCode != null) {
            patient.setHouseHoldCode(houseHoldCode);
        }

        String openmrsPhoneNumber = personAttributeMapper.getAttributeValue(openMrsPatient, PHONE_NUMBER);
        if (StringUtils.isNotBlank(openmrsPhoneNumber)) {
            PhoneNumber mciPhoneNumber = PhoneNumberMapper.map(openmrsPhoneNumber);
            patient.setPhoneNumber(mciPhoneNumber);
        }

        PersonAttribute occupation = openMrsPatient.getAttribute(OCCUPATION_ATTRIBUTE);
        if (occupation != null) {
            patient.setOccupation(bbsCodeService.getOccupationCode(occupation.toString()));
        } else {
            patient.setOccupation("");
        }

        PersonAttribute education = openMrsPatient.getAttribute(EDUCATION_ATTRIBUTE);
        if (education != null) {
            patient.setEducationLevel(bbsCodeService.getEducationCode(education.toString()));
        } else {
            patient.setEducationLevel("");
        }

        PersonAttribute hidCardIssuedStatus = openMrsPatient.getAttribute(HID_CARD_ISSUED_ATTRIBUTE);
        if (hidCardIssuedStatus != null) {
            String hidCardStatus = Boolean.valueOf(hidCardIssuedStatus.getValue()) ? HID_CARD_STATUS_ISSUED : HID_CARD_STATUS_REGISTERED;
            patient.setHidCardStatus(hidCardStatus);
        }

        patient.setAddress(addressHelper.getMciAddress(openMrsPatient));
        patient.setStatus(getMciPatientStatus(openMrsPatient));

        addRelations(openMrsPatient, patient);

        patient.setDobType(getDobType(openMrsPatient));

        return patient;
    }

    private String getAttributeValue(org.openmrs.Patient openMrsPatient, String attributeName) {
        String attributeValue = personAttributeMapper.getAttributeValue(openMrsPatient, attributeName);
        return attributeValue != null ? attributeValue : "";
    }

    private void addRelations(org.openmrs.Patient openMrsPatient, Patient patient) {
        List<Relation> relations = new RelationshipMapper().map(openMrsPatient, idMappingsRepository);
        if (CollectionUtils.isNotEmpty(relations)) {
            patient.setRelations(relations.toArray(new Relation[relations.size()]));
        }
    }

    public String getDobType(org.openmrs.Patient openMrsPatient) {
        return openMrsPatient.getBirthdateEstimated() ? DOB_TYPE_ESTIMATED : DOB_TYPE_DECLARED;
    }

    private Status getMciPatientStatus(org.openmrs.Patient openMrsPatient) {
        Status status = new Status();
        Character type = '1';
        Boolean isDead = openMrsPatient.isDead();
        if (isDead) {
            type = '2';
        }
        status.setType(type);
        status.setDateOfDeath(openMrsPatient.getDeathDate());
        return status;
    }
}
