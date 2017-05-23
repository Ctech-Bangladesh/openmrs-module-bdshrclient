package org.openmrs.module.fhir.mapper.model;

import org.hl7.fhir.dstu3.model.BaseResource;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.util.HashMap;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_URL_FORMAT;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.PROVIDER_REFERENCE_PATH;

public class EntityReferenceTest {
    @Test
    public void shouldDefaultToIdForTypesNotDefined() {
        EntityReference entityReference = new EntityReference();
        assertEquals("urn:uuid:1", entityReference.build(Integer.class, getSystemProperties(), "1"));
    }

    /**
     * NOTE: while communicating with the SHR and building encounter document, the public url should be used.
     * Because it is possible that an internal application may use SHR using IP address or url not exposed to public http
     */
    @Test
    public void shouldCreatePatientReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://mci.com/api/default/patients/1", entityReference.build(Patient.class, getSystemProperties(), "1"));
    }

    @Test
    public void shouldParseMciPatientUrl() throws Exception {
        EntityReference entityReference = new EntityReference();
        String hid = "hid";
        assertEquals(hid, entityReference.parse(Patient.class, "http://mci.com/api/v1/patient/" + hid));
    }

    @Test
    public void shouldParseEncounterUrl() {
        EntityReference entityReference = new EntityReference();
        assertEquals("enc1", entityReference.parse(Encounter.class, "http://shr.com/patients/hid1/encounters/enc1"));
    }

    @Test
    public void shouldGetEncounterIdFromResourceUrl() {
        EntityReference entityReference = new EntityReference();
        assertEquals("enc1", entityReference.parse(Encounter.class, "http://shr.com/patients/hid1/encounters/enc1#MedicationRequest/order1"));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotCreateEncounterReferenceWithOnlyId() {
        EntityReference entityReference = new EntityReference();
        entityReference.build(Encounter.class, getSystemProperties(), "1");
    }

    @Test
    public void shouldCreateEncounterReferenceFromHealthIdAndEncounterId() {
        EntityReference entityReference = new EntityReference();
        String encounterUrl = entityReference.build(Encounter.class, getSystemProperties(), new HashMap<String, String>() {{
            put(EntityReference.HEALTH_ID_REFERENCE, "hid1");
            put(EntityReference.REFERENCE_ID, "enc1");
        }});
        assertEquals("http://shr.com/patients/hid1/encounters/enc1", encounterUrl);
    }

    /**
     * NOTE: while communicating with the SHR and building encounter document, the public url should be used.
     * Because it is possible that an internal application may use SHR using IP address or url not exposed to public http
     */
    @Test
    public void shouldCreateFacilityLocationReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://fr.com/api/1.0/facilities/1234.json", entityReference.build(Location.class, getSystemProperties(), "1234"));
    }

    @Test
    public void shouldParseFacilityLocationReference() throws Exception {
        EntityReference entityReference = new EntityReference();
        String facilityId = "1013101";
        assertEquals(facilityId, entityReference.parse(Location.class, "http://public.com/api/1.0/facilities/1013101.json" + facilityId + ".json"));
    }

    @Test
    public void shouldCreateProviderReference() {
        EntityReference entityReference = new EntityReference();
        assertEquals("http://example.com/api/1.0/providers/1234.json", entityReference.build(Provider.class, getSystemProperties(), "1234"));
    }

    @Test
    public void shouldParseProviderUrl() {
        EntityReference entityReference = new EntityReference();
        assertEquals("1234", entityReference.parse(Provider.class, "http://example.com/api/1.0/providers/1234.json"));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotCreateFHIRResourceReferenceWithOnlyId() {
        EntityReference entityReference = new EntityReference();
        entityReference.build(BaseResource.class, getSystemProperties(), "1234");
    }

    @Test
    public void shouldCreateFHIRResourceUrl() {
        EntityReference entityReference = new EntityReference();
        String resourceUrl = entityReference.build(BaseResource.class, getSystemProperties(), new HashMap<String, String>() {{
            put(EntityReference.HEALTH_ID_REFERENCE, "hid1");
            put(EntityReference.ENCOUNTER_ID_REFERENCE, "enc1");
            put(EntityReference.REFERENCE_RESOURCE_NAME, "MedicationRequest");
            put(EntityReference.REFERENCE_ID, "resource-1");
        }});
        assertEquals("http://shr.com/patients/hid1/encounters/enc1#MedicationRequest/resource-1", resourceUrl);

    }

    private SystemProperties getSystemProperties() {
        Properties shrProperties = new Properties();
        shrProperties.put(PropertyKeyConstants.SHR_REFERENCE_PATH, "http://shr.com/");
        shrProperties.put(PropertyKeyConstants.SHR_PATIENT_ENC_PATH_PATTERN, "patients/%s/encounters");

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
        mciProperties.put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/default/patients");

        return new SystemProperties(frProperties, trProperties, prPoperties, facilityInstanceProperties, mciProperties, shrProperties);
    }
}