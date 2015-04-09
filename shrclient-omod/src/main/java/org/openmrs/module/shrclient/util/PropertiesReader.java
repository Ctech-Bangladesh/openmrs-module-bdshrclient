package org.openmrs.module.shrclient.util;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;


/**
 * reads properties from property files
 */
@Component("bdshrPropertiesReader")
public class PropertiesReader {
    public static final String URL_SEPARATOR_FOR_CONTEXT_PATH = "/";

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

    private Map<String, Properties> allProperties = new HashMap<String, Properties>();

    public HashMap<String, String> getBaseUrls(){
        HashMap<String, String> baseUrls = new HashMap<>();
        baseUrls.put("mci", getMciBaseUrl()) ;
        baseUrls.put("fr", getFrBaseUrl()) ;
        baseUrls.put("shr", getShrBaseUrl()) ;
        baseUrls.put("lr", getLrBaseUrl()) ;
        baseUrls.put("tr",getTrBaseUrl());
        baseUrls.put("pr", getPrBaseUrl());
        return baseUrls;
    }

    private String getTrBaseUrl() {
        Properties properties = getTrProperties();
        return getBaseUrl(properties.getProperty("tr.scheme"), properties.getProperty("tr.host"),
                properties.getProperty("tr.port"), null);
    }

    public String getMciBaseUrl() {
        Properties properties = getMciProperties();
        return properties.getProperty(MCI_REFERENCE_PATH).trim();
    }

    public String getShrBaseUrl() {
        Properties properties = getShrProperties();
        return properties.getProperty(SHR_REFERENCE_PATH).trim();
//        String shrUrl = getBaseUrl(properties.getProperty("shr.scheme"),
//                properties.getProperty("shr.host"),
//                properties.getProperty("shr.port"), null);
//        String shrVersion = properties.getProperty("shr.version");
//        return StringUtils.isEmpty(shrVersion)? shrUrl : String.format("%s/%s", shrUrl, shrVersion);
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
        return properties.getProperty(PROVIDER_REFERENCE_PATH);
    }

    public String getIdentityServerBaseUrl() {
        Properties properties = getIdentityProperties();
        return getBaseUrl(properties.getProperty("idP.scheme"),
                properties.getProperty("idP.host"),
                properties.getProperty("idP.port"), null);
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
            final File file = new File(System.getProperty("user.home") + File.separator + ".OpenMRS" + File.separator + resourceName);
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

    public String getMciPatientContext() {
        return getMciProperties().getProperty(PropertyKeyConstants.MCI_PATIENT_CONTEXT).trim();
    }

    public String getShrCatchmentPathPattern() {
        return getShrProperties().getProperty(PropertyKeyConstants.SHR_CATCHMENT_PATH_PATTERN).trim();
    }

    public String getShrPatientEncPathPattern() {
        return getShrProperties().getProperty(PropertyKeyConstants.SHR_PATIENT_ENC_PATH_PATTERN).trim();
    }
}
