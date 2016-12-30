package org.openmrs.module.shrclient.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.*;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.fhir.utils.MCIConstants;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.mapper.PersonAttributeMapper;
import org.openmrs.module.shrclient.mapper.PhoneNumberMapper;
import org.openmrs.module.shrclient.mapper.RelationshipMapper;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.PatientIdMapping;
import org.openmrs.module.shrclient.model.Status;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.service.EMRPatientDeathService;
import org.openmrs.module.shrclient.service.EMRPatientService;
import org.openmrs.module.shrclient.util.AddressHelper;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.module.shrclient.util.SystemUserService;
import org.openmrs.serialization.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.openmrs.module.fhir.OpenMRSConstants.*;
import static org.openmrs.module.fhir.utils.MCIConstants.DOB_TYPE_ESTIMATED;
import static org.openmrs.module.fhir.utils.MCIConstants.HID_CARD_STATUS_ISSUED;
import static org.openmrs.module.fhir.utils.MCIConstants.HID_CARD_STATUS_REGISTERED;

@Service("hieEmrPatientService")
public class EMRPatientServiceImpl implements EMRPatientService {
    private static final Logger logger = Logger.getLogger(EMRPatientServiceImpl.class);
    public static final String REGEX_TO_MATCH_MULTIPLE_WHITE_SPACE = "\\s+";

    private BbsCodeService bbsCodeService;
    private PatientService patientService;
    private IdMappingRepository idMappingsRepository;
    private PropertiesReader propertiesReader;
    private SystemUserService systemUserService;
    private PersonAttributeMapper personAttributeMapper;
    private EMRPatientDeathService patientDeathService;
    private GlobalPropertyLookUpService globalPropertyLookUpService;
    private LocationService locationService;

    @Autowired
    public EMRPatientServiceImpl(BbsCodeService bbsCodeService,
                                 PatientService patientService,
                                 PersonService personService,
                                 IdMappingRepository idMappingRepository,
                                 PropertiesReader propertiesReader,
                                 SystemUserService systemUserService,
                                 EMRPatientDeathService patientDeathService,
                                 GlobalPropertyLookUpService globalPropertyLookUpService,
                                 LocationService locationService) {
        this.bbsCodeService = bbsCodeService;
        this.patientService = patientService;
        this.idMappingsRepository = idMappingRepository;
        this.propertiesReader = propertiesReader;
        this.systemUserService = systemUserService;
        this.patientDeathService = patientDeathService;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
        this.locationService = locationService;
        this.personAttributeMapper = new PersonAttributeMapper(personService);
    }

    @Override
    public org.openmrs.Patient createOrUpdateEmrPatient(Patient mciPatient) {
        try {
            AddressHelper addressHelper = new AddressHelper();
            org.openmrs.Patient emrPatient = getEMRPatientByHealthId(mciPatient.getHealthId());
            if (emrPatient == null) {
                emrPatient = new org.openmrs.Patient();
            }
            emrPatient.setGender(mciPatient.getGender());
            setIdentifier(emrPatient);
            setPersonName(emrPatient, mciPatient);
            setDeathInfo(emrPatient, mciPatient);
            PersonAddress personAddress = emrPatient.getPersonAddress();
            if (personAddress != null) {
                emrPatient.removeAddress(personAddress);
            }
            emrPatient.addAddress(addressHelper.setPersonAddress(mciPatient.getAddress()));

            PatientIdentifier healthID = new PatientIdentifier();
            healthID.setIdentifier(mciPatient.getHealthId());
            healthID.setIdentifierType(patientService.getPatientIdentifierTypeByName(HEALTH_ID_IDENTIFIER_TYPE_NAME));
            healthID.setLocation(locationService.getLocation(Location.LOCATION_UNKNOWN));
            emrPatient.addIdentifier(healthID);

            addPersonAttribute(emrPatient, HEALTH_ID_ATTRIBUTE, mciPatient.getHealthId());
            addPersonAttribute(emrPatient, NATIONAL_ID_ATTRIBUTE, mciPatient.getNationalId());
            addPersonAttribute(emrPatient, BIRTH_REG_NO_ATTRIBUTE, mciPatient.getBirthRegNumber());
            addPersonAttribute(emrPatient, HOUSE_HOLD_CODE_ATTRIBUTE, mciPatient.getHouseHoldCode());
            addPersonAttribute(emrPatient, ADDRESS_CODE_ATTRIBUTE_TYPE, mciPatient.getAddress().getAddressCode());

            String hidCardStatus = mciPatient.getHidCardStatus();
            hidCardStatus = HID_CARD_STATUS_ISSUED.equalsIgnoreCase(hidCardStatus) ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
            addPersonAttribute(emrPatient, HID_CARD_ISSUED_ATTRIBUTE, hidCardStatus);

            String banglaName = mciPatient.getBanglaName();
            if (StringUtils.isNotBlank(banglaName)) {
                banglaName = banglaName.replaceAll(REGEX_TO_MATCH_MULTIPLE_WHITE_SPACE, " ");
            }
            addPersonAttribute(emrPatient, GIVEN_NAME_LOCAL, getGivenNameLocal(banglaName));
            addPersonAttribute(emrPatient, FAMILY_NAME_LOCAL, getFamilyNameLocal(banglaName));
            addPersonAttribute(emrPatient, PHONE_NUMBER, PhoneNumberMapper.map(mciPatient.getPhoneNumber()));

            mapRelations(emrPatient, mciPatient);
            setOccupation(mciPatient, emrPatient);
            setEducation(mciPatient, emrPatient);

            Date dob = mciPatient.getDateOfBirth();
            emrPatient.setBirthdate(dob);
            emrPatient.setBirthtime(dob);
            emrPatient.setBirthdateEstimated(Boolean.FALSE);
            if (DOB_TYPE_ESTIMATED.equals(mciPatient.getDobType())) {
                emrPatient.setBirthdateEstimated(Boolean.TRUE);
            }

            org.openmrs.Patient patient = patientService.savePatient(emrPatient);
            systemUserService.setOpenmrsShrSystemUserAsCreator(emrPatient);
            addPatientToIdMapping(patient, mciPatient.getHealthId());
            return emrPatient;
        } catch (Exception e) {
            logger.error(String.format("error Occurred while trying to process Patient[%s] from MCI.", mciPatient.getHealthId()), e);

            throw new RuntimeException(e);
        }
    }

    private void setEducation(Patient mciPatient, org.openmrs.Patient emrPatient) {
        String educationLevel = mciPatient.getEducationLevel();
        if (StringUtils.isBlank(educationLevel)) {
            PersonAttribute educationAttribute = emrPatient.getAttribute(EDUCATION_ATTRIBUTE);
            if (null != educationAttribute) {
                emrPatient.removeAttribute(educationAttribute);
            }
            return;
        }
        String educationConceptName = bbsCodeService.getEducationConceptName(educationLevel);
        String educationConceptId = getConceptId(educationConceptName);
        if (educationConceptId != null) {
            addPersonAttribute(emrPatient, EDUCATION_ATTRIBUTE, educationConceptId);
        } else {
            logger.warn(String.format("Can't update education for patient. " +
                            "Can't identify relevant concept for patient hid:%s, education:%s, code:%s",
                    mciPatient.getHealthId(), educationConceptName, educationLevel));
        }
    }

    private void setOccupation(Patient mciPatient, org.openmrs.Patient emrPatient) {
        String occupation = mciPatient.getOccupation();
        if (StringUtils.isBlank(occupation)) {
            PersonAttribute occupationAttribute = emrPatient.getAttribute(OCCUPATION_ATTRIBUTE);
            if (null != occupationAttribute) {
                emrPatient.removeAttribute(occupationAttribute);
            }
            return;
        }
        String occupationConceptName = bbsCodeService.getOccupationConceptName(occupation);
        String occupationConceptId = getConceptId(occupationConceptName);
        if (occupationConceptId != null) {
            addPersonAttribute(emrPatient, OCCUPATION_ATTRIBUTE, occupationConceptId);
        } else {
            logger.warn(String.format("Can't update occupation for patient. " +
                            "Can't identify relevant concept for patient hid:%s, occupation:%s, code:%s",
                    mciPatient.getHealthId(), occupationConceptName, occupation));
        }
    }

    @Override
    public org.openmrs.Patient getEMRPatientByHealthId(String healthId) {
        PatientIdMapping patientIdMapping = (PatientIdMapping) idMappingsRepository.findByExternalId(healthId, IdMappingType.PATIENT);
        if (patientIdMapping == null) return null;
        logger.info("Patient with HealthId " + healthId + " already exists. Using reference to the patient for downloaded encounters.");
        return patientService.getPatientByUuid(patientIdMapping.getInternalId());
    }

    @Override
    public void savePatient(org.openmrs.Patient emrPatient) {
        patientService.savePatient(emrPatient);
    }

    @Override
    public void mergePatients(org.openmrs.Patient toBeRetainedPatient, org.openmrs.Patient toBeRetiredPatient) throws SerializationException {
        patientService.mergePatients(toBeRetainedPatient, toBeRetiredPatient);
    }

    private void mapRelations(org.openmrs.Patient emrPatient, Patient mciPatient) {
        new RelationshipMapper().addRelationAttributes(mciPatient.getRelations(), emrPatient, idMappingsRepository);
    }

    private PatientIdentifier generateIdentifier() {
        IdentifierSourceService identifierSourceService = Context.getService(IdentifierSourceService.class);
        PatientIdentifierType identifierType = getPatientIdentifierType();
        IdentifierSource idSource = getIdentifierSource(identifierSourceService, identifierType);
        String identifier = identifierSourceService.generateIdentifier(idSource, "MCI Patient");
        return new PatientIdentifier(identifier, identifierType, null);
    }

    private IdentifierSource getIdentifierSource(IdentifierSourceService identifierSourceService, PatientIdentifierType identifierType) {
        String defaultPatientIdSourceId = globalPropertyLookUpService.getGlobalPropertyValue(MRSProperties.GLOBAL_PROPERTY_DEFAULT_IDENTIFIER_TYPE_ID);
        if (defaultPatientIdSourceId != null)
            return identifierSourceService.getIdentifierSource(Integer.parseInt(defaultPatientIdSourceId));
        else {
            List<IdentifierSource> allIdentifierSources = identifierSourceService.getAllIdentifierSources(false);
            for (IdentifierSource identifierSource : allIdentifierSources) {
                if (identifierSource.getIdentifierType().equals(identifierType)) {
                    return identifierSource;
                }
            }
        }
        return null;
    }

    private String getFamilyNameLocal(String banglaName) {
        if (StringUtils.isBlank(banglaName)) {
            return null;
        }
        int lastIndexOfSpace = banglaName.lastIndexOf(" ");
        return (-1 != lastIndexOfSpace ? banglaName.substring(lastIndexOfSpace + 1) : "");
    }

    private String getGivenNameLocal(String banglaName) {
        if (StringUtils.isBlank(banglaName)) {
            return null;
        }
        int lastIndexOfSpace = banglaName.lastIndexOf(" ");
        return (-1 != lastIndexOfSpace ? banglaName.substring(0, lastIndexOfSpace) : banglaName);
    }

    private void setDeathInfo(org.openmrs.Patient emrPatient, Patient mciPatient) {
        Status status = mciPatient.getStatus();
        boolean isAliveMciPatient = status.getType() == '1';
        boolean isAliveEmrPatient = !emrPatient.isDead();
        if (isAliveMciPatient && isAliveEmrPatient) return;
        if (isAliveMciPatient) {
            emrPatient.setDead(false);
            emrPatient.setCauseOfDeath(null);
            emrPatient.setDeathDate(null);
        } else {
            emrPatient.setDead(true);
            emrPatient.setDeathDate(status.getDateOfDeath());
            emrPatient.setCauseOfDeath(patientDeathService.getCauseOfDeath(emrPatient));
        }
    }

    private String getConceptId(String conceptName) {
        if (conceptName == null) {
            return null;
        }
        Concept concept = Context.getConceptService().getConceptByName(conceptName);
        return concept != null ? String.valueOf(concept.getConceptId()) : null;
    }

    private void setIdentifier(org.openmrs.Patient emrPatient) {
        PatientIdentifier patientIdentifier = emrPatient.getPatientIdentifier();
        if (patientIdentifier == null) {
            patientIdentifier = generateIdentifier();
            patientIdentifier.setPreferred(true);
            emrPatient.addIdentifier(patientIdentifier);
        }
    }

    private void setPersonName(org.openmrs.Patient emrPatient, Patient mciPatient) {
        PersonName emrPersonName = emrPatient.getPersonName();
        if (emrPersonName == null) {
            emrPersonName = new PersonName();
            emrPersonName.setPreferred(true);
            emrPatient.addName(emrPersonName);
        }
        emrPersonName.setGivenName(mciPatient.getGivenName());
        emrPersonName.setFamilyName(mciPatient.getSurName());
    }

    private void addPersonAttribute(org.openmrs.Patient emrPatient, String attributeName, String attributeValue) {
        PersonAttribute attribute = personAttributeMapper.getAttribute(attributeName, attributeValue);
        if (attribute != null) {
            emrPatient.addAttribute(attribute);
        } else {
            System.out.println("Attribute not defined: " + attributeName);
        }
    }

    private PatientIdentifierType getPatientIdentifierType() {
        AdministrationService administrationService = Context.getAdministrationService();
        String globalProperty = administrationService.getGlobalProperty(MRSProperties.GLOBAL_PROPERTY_EMR_PRIMARY_IDENTIFIER_TYPE);
        PatientIdentifierType patientIdentifierByUuid = Context.getPatientService().getPatientIdentifierTypeByUuid(globalProperty);
        return patientIdentifierByUuid;
    }

    private void addPatientToIdMapping(org.openmrs.Patient emrPatient, String healthId) {
        String patientUuid = emrPatient.getUuid();
        SystemProperties systemProperties = new SystemProperties(
                propertiesReader.getFrProperties(),
                propertiesReader.getTrProperties(),
                propertiesReader.getPrProperties(),
                propertiesReader.getFacilityInstanceProperties(),
                propertiesReader.getMciProperties(), new Properties());
        String url = new EntityReference().build(org.openmrs.Patient.class, systemProperties, healthId);
        idMappingsRepository.saveOrUpdateIdMapping(new PatientIdMapping(patientUuid, healthId, url, new Date()));
    }
}

