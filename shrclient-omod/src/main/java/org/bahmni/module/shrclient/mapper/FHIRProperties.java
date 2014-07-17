package org.bahmni.module.shrclient.mapper;

import java.util.HashMap;
import java.util.Map;

public class FHIRProperties {
    public static final String FHIR_CONDITION_CODE_DIAGNOSIS = "diagnosis";
    public static final String FHIR_CONDITION_CATEGORY_URL = "http://hl7.org/fhir/vs/condition-category";
    public static final String FHIR_CONDITION_SEVERITY_URL = "http://hl7.org/fhir/vs/condition-severity";
    public static final String SNOMED_VALUE_MODERATE_SEVERTY = "6736007";
    public static final String SNOMED_VALUE_SEVERE_SEVERITY = "24484000";
    public static final String FHIR_SEVERITY_MODERATE = "Moderate";
    public static final String FHIR_SEVERITY_SEVERE = "Severe";

    private Map<String, String> severityCodes = new HashMap<String, String>();

    public FHIRProperties () {
        severityCodes.put(FHIR_SEVERITY_MODERATE, SNOMED_VALUE_MODERATE_SEVERTY);
        severityCodes.put(FHIR_SEVERITY_SEVERE, SNOMED_VALUE_SEVERE_SEVERITY);
    }

    public String getSeverityCode(String severity) {
        return severityCodes.get(severity);
    }
}
