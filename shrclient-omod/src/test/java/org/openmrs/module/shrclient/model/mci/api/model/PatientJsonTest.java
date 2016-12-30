package org.openmrs.module.shrclient.model.mci.api.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.shrclient.model.Address;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.Relation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.Assert.assertEquals;

public class PatientJsonTest {

    private Patient patient;
    private ObjectMapper objectMapper;

    public PatientJsonTest() {
    }

    @Before
    public void setup() {
        
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        patient = new Patient();
        patient.setGivenName("Scott");
        patient.setSurName("Tiger");
        patient.setGender("M");
        Address address = new Address();
        address.setDivisionId("10");
        address.setDistrictId("04");
        address.setUpazilaId("09");
        address.setCityCorporationId("20");
        address.setUnionOrUrbanWardId("01");
        patient.setAddress(address);
    }

    @Test
    public void shouldExcludeEmptyNonMandatoryFields() throws Exception {
        String expected = "{\"nid\":null,\"given_name\":\"Scott\",\"sur_name\":\"Tiger\"," +
                "\"date_of_birth\":null,\"dob_type\":\"1\",\"gender\":\"M\",\"occupation\":null," +
                "\"edu_level\":null,\"present_address\":{\"address_line\":null,\"division_id\":\"10\"," +
                "\"district_id\":\"04\",\"upazila_id\":\"09\",\"city_corporation_id\":\"20\"," +
                "\"union_or_urban_ward_id\":\"01\"},\"status\":null,\"bin_brn\":null,\"household_code\":null,\"hid_card_status\":null}";
        patient.setDobType("1");
        String actual = objectMapper.writeValueAsString(patient);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldIncludeNonEmptyNonMandatoryFields() throws Exception {
        patient.setNationalId("nid-100");
        patient.setActive(true);
        patient.setDobType("2");

        String expected = "{\"nid\":\"nid-100\",\"given_name\":\"Scott\",\"sur_name\":\"Tiger\"," +
                "\"date_of_birth\":null,\"dob_type\":\"2\",\"gender\":\"M\",\"occupation\":null," +
                "\"edu_level\":null,\"present_address\":{\"address_line\":null,\"division_id\":\"10\"," +
                "\"district_id\":\"04\",\"upazila_id\":\"09\",\"city_corporation_id\":\"20\"," +
                "\"union_or_urban_ward_id\":\"01\"},\"status\":null,\"bin_brn\":null,\"household_code\":null," +
                "\"active\":true,\"hid_card_status\":null}";
        String actual = objectMapper.writeValueAsString(patient);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldIncludeRelationsForPatient() throws Exception {
        patient.setNationalId("nid-100");
        patient.setRelations(getRelationsForPatient(patient));
        patient.setActive(false);
        String expected = "{\"nid\":\"nid-100\",\"given_name\":\"Scott\",\"sur_name\":\"Tiger\"," +
                "\"date_of_birth\":null,\"dob_type\":null,\"gender\":\"M\",\"occupation\":null," +
                "\"edu_level\":null,\"present_address\":{\"address_line\":null,\"division_id\":\"10\"," +
                "\"district_id\":\"04\",\"upazila_id\":\"09\",\"city_corporation_id\":\"20\"," +
                "\"union_or_urban_ward_id\":\"01\"},\"status\":null,\"bin_brn\":null,\"household_code\":null," +
                "\"relations\":[{\"type\":\"mother\",\"given_name\":\"Mother of Scott\",\"sur_name\":\"Tiger\"}]," +
                "\"active\":false,\"hid_card_status\":null}";
        String actual = objectMapper.writeValueAsString(patient);
        assertEquals(expected, actual);
    }

    @Test
    public void shouldCreateAPatientFromJson() throws Exception {
        Patient patientFromJson = objectMapper.readValue(asString("patients_response/by_hid.json"), Patient.class);
        assertEquals("11421467785", patientFromJson.getHealthId());
        assertEquals("7654376543127", patientFromJson.getNationalId());
        assertEquals("F", patientFromJson.getGender());
        assertEquals("HouseHold", patientFromJson.getGivenName());
        assertEquals("1", patientFromJson.getDobType());
    }

    private String asString(String filePath) throws IOException {
        URL resource = URLClassLoader.getSystemResource(filePath);
        return FileUtils.readFileToString(new File(resource.getPath()));
    }

    private Relation[] getRelationsForPatient(Patient patient) {
        Relation aRel = new Relation();
        aRel.setGivenName("Mother of " + patient.getGivenName());
        aRel.setSurName(patient.getSurName());
        aRel.setType("mother");
        Relation[] relations = new Relation[1];
        relations[0] = aRel;
        return relations;
    }
}