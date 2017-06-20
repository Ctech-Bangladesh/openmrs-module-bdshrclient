package org.openmrs.module.shrclient.util;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import static org.openmrs.module.fhir.utils.PropertyKeyConstants.*;

public class SystemProperties {
    private Properties frProperties;
    private Properties trProperties;
    private Properties prProperties;
    private Properties facilityInstanceProperties;
    private Properties mciProperties;
    private Properties shrProperties;
    private Properties visitTypeProperties;

    public SystemProperties(Properties frProperties,
                            Properties trProperties, Properties prProperties,
                            Properties facilityInstanceProperties,
                            Properties mciProperties, Properties shrProperties, Properties visitTypeProperties) {
        this.frProperties = frProperties;
        this.trProperties = trProperties;
        this.prProperties = prProperties;
        this.facilityInstanceProperties = facilityInstanceProperties;
        this.mciProperties = mciProperties;
        this.shrProperties = shrProperties;
        this.visitTypeProperties = visitTypeProperties;
    }

    public String getFacilityId() {
        return facilityInstanceProperties.getProperty(FACILITY_ID);
    }

    public String getTrValuesetUrl(String valueSetKeyName) {
        String valuesetName = trProperties.getProperty(valueSetKeyName);
        if (StringUtils.isBlank(valuesetName)) {
            throw new RuntimeException("Could not identify valueset. Make sure tr properties has key:" + valueSetKeyName);
        }

        return createValueSetUrlFor(valuesetName);
    }

    public String createValueSetUrlFor(String valuesetName) {
        String trBaseUrl = StringUtil.ensureSuffix(trProperties.getProperty(PropertyKeyConstants.TR_REFERENCE_PATH), "/");
        String trValueSetPathInfo = StringUtil.removePrefix(trProperties.getProperty(TR_VALUESET_PATH_INFO), "/");

        return StringUtil.ensureSuffix(trBaseUrl + trValueSetPathInfo, "/") + valuesetName;
    }

    public String getProviderResourcePath() {
        return prProperties.getProperty(PROVIDER_REFERENCE_PATH).trim();
    }

    public String getMciPatientUrl() {
        String mciRefPath = mciProperties.getProperty(MCI_REFERENCE_PATH);
        String mciPatientCtx = mciProperties.getProperty(MCI_PATIENT_CONTEXT);
        return StringUtil.ensureSuffix(mciRefPath, "/") + StringUtil.removePrefix(mciPatientCtx, "/");
    }

    public String getShrEncounterUrl() {
        String shrRefPath = shrProperties.getProperty(SHR_REFERENCE_PATH);
        String shrPatientCtx = shrProperties.getProperty(SHR_PATIENT_ENC_PATH_PATTERN);
        return StringUtil.ensureSuffix(shrRefPath, "/") + StringUtil.removePrefix(shrPatientCtx, "/");
    }

    public String getFacilityResourcePath() {
        return frProperties.getProperty(FACILITY_REFERENCE_PATH).trim();
    }

    public HashMap<String, String> getVisitTypeToEncounterClassMap() {
        String property = visitTypeProperties.getProperty(VISIT_TYPE_TO_ENCOUNTER_CLASS_MAP);
        return getMap(property);

    }

    public HashMap<String, String> getEncounterClassToVisitTypeMap() {
        String property = visitTypeProperties.getProperty(ENCOUNTER_CLASS_TO_VISIT_TYPE_MAP);
        return getMap(property);

    }

    private HashMap<String, String> getMap(String property) {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<HashMap<String, String>> typeRef
                = new TypeReference<HashMap<String, String>>() {
        };
        try {
            return objectMapper.readValue(property, typeRef);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
