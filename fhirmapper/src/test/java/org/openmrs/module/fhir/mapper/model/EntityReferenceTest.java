package org.openmrs.module.fhir.mapper.model;

import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_ID;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_URL_FORMAT;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.PROVIDER_REFERENCE_PATH;

public class EntityReferenceTest {
    @Test
    public void shouldDefaultToIdForTypesNotDefined() {
        EntityReference entityReference = new EntityReference();
        assertEquals("urn:1", entityReference.build(Integer.class, getSystemProperties("1234"), "1"));
    }

    /**
     * NOTE: while communicating with the SHR and building encounter document, the public url should be used.
     * Because it is possible that an internal application may use SHR using IP address or url not exposed to public http
     */
    @Test
    public void shouldCreatePatientReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://mci.com/api/v1/patients/1", entityReference.build(Patient.class, getSystemProperties("1234"), "1"));
    }

    @Test
    public void shouldCreateEncounterReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("urn:1", entityReference.build(Encounter.class, getSystemProperties("1234"), "1"));
    }

    /**
     * NOTE: while communicating with the SHR and building encounter document, the public url should be used.
     * Because it is possible that an internal application may use SHR using IP address or url not exposed to public http
     */
    @Test
    public void shouldCreateFacilityLocationReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://fr.com/api/1.0/facilities/1234.json", entityReference.build(Location.class, getSystemProperties("1234"), "1234"));
    }

    @Test
    public void shouldParseFacilityLocationReference() throws Exception {
        EntityReference entityReference = new EntityReference();
        String facilityId = "1013101";
        assertEquals(facilityId, entityReference.parse(Location.class, "http://public.com/api/1.0/facilities/1013101.json" + facilityId + ".json"));
    }

    @Test
    public void shouldParseMciPatientUrl() throws Exception {
        EntityReference entityReference = new EntityReference();
        String hid = "hid";
        assertEquals(hid, entityReference.parse(Patient.class, "http://mci.com/api/v1/patient/" + hid));
    }

    @Test
    public void shouldCreateProviderReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://example.com/api/1.0/providers/1234.json", entityReference.build(EncounterProvider.class, getSystemProperties("1234"), "1234"));
    }

    @Test
    public void shouldParseProviderUrl() {
        EntityReference entityReference = new EntityReference();
        assertEquals("1234",entityReference.parse(EncounterProvider.class, "http://example.com/api/1.0/providers/1234.json"));
    }

    private SystemProperties getSystemProperties(String facilityId) {
        Properties shrProperties = new Properties();
        shrProperties.setProperty(FACILITY_ID, facilityId);
        Properties trProperties = new Properties();

        Properties frProperties = new Properties();
        frProperties.setProperty(FACILITY_URL_FORMAT, "foo-bar/%s.json");
        frProperties.setProperty(PropertyKeyConstants.FACILITY_REFERENCE_PATH, "http://fr.com/api/1.0/facilities");

        Properties prPoperties = new Properties();
        prPoperties.setProperty(PROVIDER_REFERENCE_PATH, "http://example.com/api/1.0/providers");
        Properties facilityInstanceProperties = new Properties();

        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("mci", "http://mci");
        baseUrls.put("fr", "http://fr");

        Properties mciProperties = new Properties();
        mciProperties.put(PropertyKeyConstants.MCI_REFERENCE_PATH, "http://mci.com/");
        mciProperties.put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/v1/patients");

        return new SystemProperties(baseUrls, frProperties, trProperties, prPoperties, facilityInstanceProperties, mciProperties);
    }
}