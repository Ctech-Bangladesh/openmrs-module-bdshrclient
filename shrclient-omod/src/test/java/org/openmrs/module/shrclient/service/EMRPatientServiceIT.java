package org.openmrs.module.shrclient.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.api.PatientService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.OpenMRSConstants.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class EMRPatientServiceIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private PatientService patientService;

    @Autowired
    private EMRPatientService emrPatientService;


    @Test
    public void shouldSaveAMCIPatientAsEmrPatient() throws Exception {
        executeDataSet("testDataSets/patientUpdateDS.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/by_hid.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        assertEquals("HouseHold", savedPatient.getGivenName());
        assertEquals("Patient", savedPatient.getFamilyName());
        assertEquals(savedPatient.getGender(), "F");
        assertFalse(savedPatient.getBirthdateEstimated());

        PatientIdentifier healthId = savedPatient.getPatientIdentifier(HEALTH_ID_IDENTIFIER_TYPE_NAME);
        assertEquals(healthId.getIdentifier(), "11421467785");

        assertAttribute(savedPatient, HEALTH_ID_ATTRIBUTE, "11421467785");
        assertAttribute(savedPatient, NATIONAL_ID_ATTRIBUTE, "7654376543127");
        assertAttribute(savedPatient, BIRTH_REG_NO_ATTRIBUTE, "54098540985409815");

        assertNull(savedPatient.getAttribute(HID_CARD_ISSUED_ATTRIBUTE));
    }

    @Test
    public void shouldSaveAMCIPatientWithEstimatedDOB() throws Exception {
        executeDataSet("testDataSets/patientUpdateDS.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patient_with_estimated_DOB.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        assertEquals("HouseHold", savedPatient.getGivenName());
        assertEquals("Patient", savedPatient.getFamilyName());
        assertEquals(savedPatient.getGender(), "F");
        assertTrue(savedPatient.getBirthdateEstimated());

        PatientIdentifier healthId = savedPatient.getPatientIdentifier(HEALTH_ID_IDENTIFIER_TYPE_NAME);
        assertEquals(healthId.getIdentifier(), "11421467785");

        assertAttribute(savedPatient, HEALTH_ID_ATTRIBUTE, "11421467785");
        assertAttribute(savedPatient, NATIONAL_ID_ATTRIBUTE, "7654376543127");
        assertAttribute(savedPatient, BIRTH_REG_NO_ATTRIBUTE, "54098540985409815");
    }

    @Test
    public void shouldMapRelationsToPatientAttributesWhenPresent() throws Exception {
        executeDataSet("testDataSets/patientUpdateDS.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patientWithRelations.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        PersonAttribute fatherName = savedPatient.getAttribute(FATHER_NAME_ATTRIBUTE_TYPE);
        assertNotNull(fatherName);
        assertEquals(FATHER_NAME_ATTRIBUTE_TYPE, fatherName.getAttributeType().getName());
        assertEquals("Md. Sakib Ali Khan", fatherName.getValue());

        PersonAttribute spouseName = savedPatient.getAttribute(SPOUSE_NAME_ATTRIBUTE_TYPE);
        assertNotNull(spouseName);
        assertEquals(SPOUSE_NAME_ATTRIBUTE_TYPE, spouseName.getAttributeType().getName());
        assertEquals("Azad", spouseName.getValue());
    }

    @Test
    public void shouldUpdatePatientAttributesOnDownloadIfPresentAlready() throws Exception {
        executeDataSet("testDataSets/patientUpdateDS.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patientWithRelations.json");

        emrPatientService.createOrUpdateEmrPatient(patient);
        //updated twice
        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        List<PersonAttribute> fatherName = savedPatient.getAttributes(FATHER_NAME_ATTRIBUTE_TYPE);
        assertEquals(1, fatherName.size());

        List<PersonAttribute> spouseName = savedPatient.getAttributes(SPOUSE_NAME_ATTRIBUTE_TYPE);
        assertEquals(1, spouseName.size());
    }

    @Test
    public void shouldPopulateAddressCodeAttributeOnDownload() throws Exception {
        executeDataSet("testDataSets/patientUpdateDSWithAddressCodeAttribute.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/by_hid.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        PersonAttribute addressCode = savedPatient.getAttribute(ADDRESS_CODE_ATTRIBUTE_TYPE);

        assertNotNull(addressCode);
        assertEquals("302606", addressCode.getValue());
    }

    @Test
    public void shouldNotPopulateAddressCodeWhenAttributeIsNotPresent() throws Exception {
        executeDataSet("testDataSets/patientUpdateDS.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/by_hid.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        PersonAttribute addressCode = savedPatient.getAttribute(ADDRESS_CODE_ATTRIBUTE_TYPE);

        assertNull(addressCode);
    }

    @Test
    public void shouldPopulateHidCardIssuedAttributeOnDownload() throws Exception {
        executeDataSet("testDataSets/patientUpdateDSWithHidIssuedAttribute.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/by_hid.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);
        PersonAttribute hidIssued = savedPatient.getAttribute(HID_CARD_ISSUED_ATTRIBUTE);
        assertNotNull(hidIssued);
        assertEquals("false", hidIssued.getValue());
    }


    @Test
    public void shouldUpdateAnOlderPatientAddressToNewOne() throws Exception {
        executeDataSet("testDataSets/patientUpdateDSAddressHierarchy.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patient_with_address_to_be_updated.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        PersonAddress personAddress = savedPatient.getPersonAddress();
        assertEquals("Dhaka", personAddress.getStateProvince());
        assertEquals("Gazipur", personAddress.getCountyDistrict());
        assertEquals("Kaliganj", personAddress.getAddress5());
        assertEquals("Unions Of Kaliganj Upazila", personAddress.getAddress4());
        assertEquals("Bahadursadi", personAddress.getAddress3());
        assertEquals("Ward No-01", personAddress.getAddress2());
        assertEquals("house 1", personAddress.getAddress1());

        org.openmrs.module.shrclient.model.Patient patientUpdateResponse = getPatientFromJson("patients_response/patient_with_updated_address.json");

        emrPatientService.createOrUpdateEmrPatient(patientUpdateResponse);

        Patient updatedPatient = patientService.getPatient(1);

        personAddress = updatedPatient.getPersonAddress();
        assertEquals("Dhaka", personAddress.getStateProvince());
        assertEquals("Gazipur", personAddress.getCountyDistrict());
        assertEquals("Kaliganj", personAddress.getAddress5());
        assertNull(personAddress.getAddress4());
        assertNull(personAddress.getAddress3());
        assertNull(personAddress.getAddress2());
        assertEquals("house 2", personAddress.getAddress1());
    }

    @Test
    public void shouldDeleteAllAttributesIfDeletedFromMCI() throws Exception {
        executeDataSet("testDataSets/patientUpdateDSAddressHierarchy.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patientWithAllAttributes.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        assertAttribute(savedPatient, NATIONAL_ID_ATTRIBUTE, "7654376543127");
        assertAttribute(savedPatient, BIRTH_REG_NO_ATTRIBUTE, "54098540985409815");
        assertAttribute(savedPatient, PHONE_NUMBER, "100");
        assertAttribute(savedPatient, HOUSE_HOLD_CODE_ATTRIBUTE, "124");
        assertAttribute(savedPatient, MOTHER_NAME_ATTRIBUTE_TYPE, "Shana Khan");
        assertAttribute(savedPatient, FATHER_NAME_ATTRIBUTE_TYPE, "Md. Sakib Ali Khan");
        assertAttribute(savedPatient, SPOUSE_NAME_ATTRIBUTE_TYPE, "Azad Khan");
        assertAttribute(savedPatient, OCCUPATION_ATTRIBUTE, "303");
        assertAttribute(savedPatient, EDUCATION_ATTRIBUTE, "304");

        org.openmrs.module.shrclient.model.Patient patientUpdateResponse = getPatientFromJson("patients_response/patient_with_removed_attribute.json");
        emrPatientService.createOrUpdateEmrPatient(patientUpdateResponse);

        Patient updatedPatient = patientService.getPatient(1);

        assertNull(updatedPatient.getAttribute(NATIONAL_ID_ATTRIBUTE));
        assertNull(updatedPatient.getAttribute(BIRTH_REG_NO_ATTRIBUTE));
        assertNull(updatedPatient.getAttribute(PHONE_NUMBER));
        assertNull(updatedPatient.getAttribute(HOUSE_HOLD_CODE_ATTRIBUTE));
        assertNull(updatedPatient.getAttribute(OCCUPATION_ATTRIBUTE));
        assertNull(updatedPatient.getAttribute(EDUCATION_ATTRIBUTE));
        assertNull(updatedPatient.getAttribute(MOTHER_NAME_ATTRIBUTE_TYPE));
        assertNull(updatedPatient.getAttribute(FATHER_NAME_ATTRIBUTE_TYPE));
        assertNull(updatedPatient.getAttribute(SPOUSE_NAME_ATTRIBUTE_TYPE));

    }

    @Test
    public void shouldDeleteRelationAttributesDeletedInMCI() throws Exception {
        executeDataSet("testDataSets/patientUpdateDSAddressHierarchy.xml");
        org.openmrs.module.shrclient.model.Patient patient = getPatientFromJson("patients_response/patientWithAllAttributes.json");

        emrPatientService.createOrUpdateEmrPatient(patient);

        Patient savedPatient = patientService.getPatient(1);

        assertAttribute(savedPatient, MOTHER_NAME_ATTRIBUTE_TYPE, "Shana Khan");
        assertAttribute(savedPatient, FATHER_NAME_ATTRIBUTE_TYPE, "Md. Sakib Ali Khan");
        assertAttribute(savedPatient, SPOUSE_NAME_ATTRIBUTE_TYPE, "Azad Khan");

        org.openmrs.module.shrclient.model.Patient patientUpdateResponse = getPatientFromJson("patients_response/patientWithFatherAttribute.json" +
                "");
        emrPatientService.createOrUpdateEmrPatient(patientUpdateResponse);

        Patient updatedPatient = patientService.getPatient(1);
        assertAttribute(updatedPatient, FATHER_NAME_ATTRIBUTE_TYPE, "Md. Sakib Ali Khan");
        assertNull(updatedPatient.getAttribute(MOTHER_NAME_ATTRIBUTE_TYPE));
        assertNull(updatedPatient.getAttribute(SPOUSE_NAME_ATTRIBUTE_TYPE));

    }

    private void assertAttribute(Patient savedPatient, String attributeName, String expected) {
        PersonAttribute attribute = savedPatient.getAttribute(attributeName);
        assertNotNull(attribute);
        assertEquals(expected, attribute.getValue());
    }

    private org.openmrs.module.shrclient.model.Patient getPatientFromJson(String patientJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        URL resource = URLClassLoader.getSystemResource(patientJson);
        final String patientResponse = FileUtils.readFileToString(new File(resource.getPath()));

        return mapper.readValue(patientResponse, org.openmrs.module.shrclient.model.Patient.class);
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}
