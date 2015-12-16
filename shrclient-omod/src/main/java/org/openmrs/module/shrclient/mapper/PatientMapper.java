package org.openmrs.module.shrclient.mapper;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.PersonAttribute;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.PhoneNumber;
import org.openmrs.module.shrclient.model.Relation;
import org.openmrs.module.shrclient.model.Status;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.util.AddressHelper;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.util.List;

import static org.openmrs.module.fhir.Constants.*;

public class PatientMapper {
    private static final String DOB_TYPE_DECLARED = "1";
    private static final String DOB_TYPE_ESTIMATED = "3";
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

    public Patient map(org.openmrs.Patient openMrsPatient, SystemProperties systemProperties) {
        Patient patient = new Patient();
        String nationalId = personAttributeMapper.getAttributeValue(openMrsPatient, NATIONAL_ID_ATTRIBUTE);
        if (nationalId != null) {
            patient.setNationalId(nationalId);
        }

        String healthId = personAttributeMapper.getAttributeValue(openMrsPatient, HEALTH_ID_ATTRIBUTE);
        if (healthId != null) {
            patient.setHealthId(healthId);
        }

        String birthRegNo = personAttributeMapper.getAttributeValue(openMrsPatient, BIRTH_REG_NO_ATTRIBUTE);
        if (birthRegNo != null) {
            patient.setBirthRegNumber(birthRegNo);
        }

        String houseHoldCode = personAttributeMapper.getAttributeValue(openMrsPatient, HOUSE_HOLD_CODE_ATTRIBUTE);
        if (houseHoldCode != null) {
            patient.setHouseHoldCode(houseHoldCode);
        }

        String givenNameLocal = personAttributeMapper.getAttributeValue(openMrsPatient, GIVEN_NAME_LOCAL);
        String familyNameLocal = personAttributeMapper.getAttributeValue(openMrsPatient, FAMILY_NAME_LOCAL);
        String banglaName = (StringUtils.isNotBlank(givenNameLocal) ? givenNameLocal : "")
                .concat(" ")
                .concat((StringUtils.isNotBlank(familyNameLocal) ? familyNameLocal : "")).trim();

        if (StringUtils.isNotBlank(banglaName)) {
            patient.setBanglaName(banglaName);
        }

        String openmrsPhoneNumber = personAttributeMapper.getAttributeValue(openMrsPatient, PHONE_NUMBER);
        if (StringUtils.isNotBlank(openmrsPhoneNumber)) {
            PhoneNumber mciPhoneNumber = PhoneNumberMapper.map(openmrsPhoneNumber);
            patient.setPhoneNumber(mciPhoneNumber);
        }

        patient.setGivenName(openMrsPatient.getGivenName());
        patient.setSurName(openMrsPatient.getFamilyName());
        patient.setGender(openMrsPatient.getGender());
        patient.setDateOfBirth(openMrsPatient.getBirthdate());

        PersonAttribute occupation = openMrsPatient.getAttribute(OCCUPATION_ATTRIBUTE);
        if (occupation != null) {
            patient.setOccupation(bbsCodeService.getOccupationCode(occupation.toString()));
        }

        PersonAttribute education = openMrsPatient.getAttribute(EDUCATION_ATTRIBUTE);
        if (education != null) {
            patient.setEducationLevel(bbsCodeService.getEducationCode(education.toString()));
        }

        patient.setAddress(addressHelper.getMciAddress(openMrsPatient));
        patient.setStatus(getMciPatientStatus(openMrsPatient));
        List<Relation> relations = new RelationshipMapper().map(openMrsPatient, idMappingsRepository);
        if (CollectionUtils.isNotEmpty(relations)) {
            patient.setRelations(relations.toArray(new Relation[relations.size()]));
        }

        patient.setDobType(getDobType(openMrsPatient));

        return patient;
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
