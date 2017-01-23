package org.openmrs.module.shrclient.util;

import org.junit.Test;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;

import java.util.HashMap;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;

public class SystemPropertiesTest {
    @Test
    public void shouldReadFacilityId() throws Exception {
        Properties instanceProperties = new Properties();
        instanceProperties.setProperty(FACILITY_ID, "foo");
        SystemProperties systemProperties = new SystemProperties(new Properties(), new Properties(), new Properties(), instanceProperties, null, new Properties());
        assertThat(systemProperties.getFacilityId(), is("foo"));
    }


    /**
     * TODO: Whats the intent of the test?
     * @throws Exception
     */
    @Test
    public void shouldReadFRProperties() throws Exception {
        Properties frProperties = new Properties();
        frProperties.setProperty(FACILITY_URL_FORMAT, "bar");
        SystemProperties systemProperties = new SystemProperties(
                frProperties, new Properties(), new Properties(), new Properties(),
                null, new Properties());
        //assertThat(systemProperties.getFacilityUrlFormat(), is("bar"));
    }

    @Test
    public void shouldReadBaseUrls() throws Exception {
        Properties mciProperties = new Properties();
        mciProperties.put(PropertyKeyConstants.MCI_REFERENCE_PATH, "https://mci.com/");
        mciProperties.put(PropertyKeyConstants.MCI_PATIENT_CONTEXT, "/api/default/patients");


        Properties frProperties = new Properties();
        frProperties.setProperty(FACILITY_REFERENCE_PATH, "https://fr.com");

        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("mci", "https://mci.com");
        baseUrls.put("fr", "https://fr.com");
        SystemProperties systemProperties = new SystemProperties(frProperties, new Properties(), new Properties(), new Properties(),mciProperties, new Properties());
        assertThat(systemProperties.getMciPatientUrl(), is("https://mci.com/api/default/patients"));
        assertThat(systemProperties.getFacilityResourcePath(), is("https://fr.com"));
    }

    @Test
    public void shouldReadValueSetUrls() throws Exception {
        Properties trProperties = new Properties();
        trProperties.setProperty(PropertyKeyConstants.TR_REFERENCE_PATH, "http://172.18.46.56:9080");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_PATH_INFO, "openmrs/ws/rest/v1/tr/vs");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_ROUTE, "Route-of-Administration");
        trProperties.setProperty(PropertyKeyConstants.TR_VALUESET_QUANTITY_UNITS, "Quantity-Units");
        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("tr", "http://172.18.46.56:9080");
        SystemProperties systemProperties = new SystemProperties(new Properties(), trProperties, new Properties(), new Properties(),null, new Properties());
        assertThat(systemProperties.getTrValuesetUrl(PropertyKeyConstants.TR_VALUESET_ROUTE), is("http://172.18.46.56:9080/openmrs/ws/rest/v1/tr/vs/Route-of-Administration"));
        assertThat(systemProperties.getTrValuesetUrl(PropertyKeyConstants.TR_VALUESET_QUANTITY_UNITS), is("http://172.18.46.56:9080/openmrs/ws/rest/v1/tr/vs/Quantity-Units"));
    }
}