package org.openmrs.module.shrclient.util;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;


/**
 * reads properties from property files
 */
@Component("bdshrPropertiesReader")
public class PropertiesReader {

    public Properties getMciProperties() {
        return getProperties("mci.properties");
    }

    public Properties getShrProperties() {
        return getProperties("shr.properties");
    }

    public Properties getLrProperties() {
        return getProperties("lr.properties");
    }

    public Properties getFrProperties() {
        return getProperties("fr.properties");
    }

    public Properties getPrProperties() {
        return getProperties("pr.properties");
    }

    public Properties getTrProperties() {
        return getProperties("tr_atomfeed_properties.properties");
    }

    public Properties getIdentityProperties() {
        return getProperties("identity.properties");
    }

    public Properties getFacilityInstanceProperties() {
        return getProperties("facility_instance.properties");
    }

    public Properties getFhirMappingProperties() {
        return getProperties("fhir_mapping.properties");
    }

    private Map<String, Properties> allProperties = new HashMap<String, Properties>();

    private String getTrBaseUrl() {
        Properties properties = getTrProperties();
        return properties.getProperty(PropertyKeyConstants.TR_REFERENCE_PATH).trim();
    }

    public String getMciBaseUrl() {
        Properties properties = getMciProperties();
        return properties.getProperty(MCI_REFERENCE_PATH).trim();
    }

    public String getShrBaseUrl() {
        Properties properties = getShrProperties();
        return properties.getProperty(SHR_REFERENCE_PATH).trim();
    }

    public String getLrBaseUrl() {
        Properties properties = getLrProperties();
        return properties.getProperty(LOCATION_REFERENCE_PATH);
    }

    public String getFrBaseUrl() {
        Properties properties = getFrProperties();
        return properties.getProperty(FACILITY_REFERENCE_PATH);
    }

    public String getPrBaseUrl() {
        Properties properties = getPrProperties();
        return properties.getProperty(PROVIDER_REFERENCE_PATH).trim();
    }

    public String getIdPBaseUrl() {
        Properties properties = getIdentityProperties();
        return properties.getProperty(IDP_SERVER_URL).trim();
    }

    private String getBaseUrl(String scheme, String host, String port, String contextPath) {
        String rootUrl = String.format("%s://%s", scheme, host, getValidPort(port));
        if (!StringUtils.isBlank(contextPath)) {
            return rootUrl + contextPath;
        } else {
            return rootUrl;
        }
    }

    private String getValidPort(String port) {
        if (StringUtils.isBlank(port)) {
            return "";
        } else {
            return Integer.valueOf(port.trim()).toString();
        }
    }

    private Properties getProperties(String resourceName) {
        Properties resourceProperties = allProperties.get(resourceName);
        if (resourceProperties != null) return resourceProperties;
        try {
            Properties properties = new Properties();
            final File file = new File(OpenmrsUtil.getApplicationDataDirectory(), resourceName);
            final InputStream inputStream;
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
            }
            properties.load(inputStream);
            allProperties.put(resourceName, properties);
            return properties;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPropertiesLocation() {
        String propertiesLocation = System.getProperty("user.home") + File.separator + ".OpenMRS" + File.separator;
        File propertiesDirectory = new File(propertiesLocation);
        if (propertiesDirectory.exists() && propertiesDirectory.isDirectory())
            return propertiesLocation;
        return "/opt/openmrs/etc";
    }

    public String getMciPatientContext() {
        return getMciProperties().getProperty(PropertyKeyConstants.MCI_PATIENT_CONTEXT).trim();
    }

    public int getMciMaxFailedEvent() {
        String mciMaxFailedEventCount = getMciProperties().getProperty(PropertyKeyConstants.MCI_MAX_FAILED_EVENT);
        return getMaxFailedEventCount(mciMaxFailedEventCount);
    }

    public String getMCICatchmentPathPattern() {
        return getMciProperties().getProperty(PropertyKeyConstants.MCI_PATIENT_CATCHMENT_PATH_PATTERN).trim();
    }

    public List<String> getFacilityCatchments() {
        String facilityCatchmentString = getFacilityInstanceProperties().getProperty(FACILITY_CATCHMENTS).toString();

        return Arrays.asList(facilityCatchmentString.split(","));
    }

    public int getShrMaxFailedEvent() {
        String shrMaxFailedEventCount = getShrProperties().getProperty(PropertyKeyConstants.SHR_MAX_FAILED_EVENT);
        return getMaxFailedEventCount(shrMaxFailedEventCount);
    }

    public String getShrCatchmentPathPattern() {
        return getShrProperties().getProperty(PropertyKeyConstants.SHR_CATCHMENT_PATH_PATTERN).trim();
    }

    public String getShrPatientEncPathPattern() {
        return getShrProperties().getProperty(PropertyKeyConstants.SHR_PATIENT_ENC_PATH_PATTERN).trim();
    }

    public String getIdPSignInPath() {
        return getIdentityProperties().getProperty(PropertyKeyConstants.IDP_SIGNIN_PATH).trim();
    }

    private int getMaxFailedEventCount(String failedEventCount) {
        if (StringUtils.isNotBlank(failedEventCount)) {
            try {
                return Integer.parseInt(failedEventCount.trim());
            } catch (Exception e) {
                // do nothing
            }
        }
        return 100;
    }
}
