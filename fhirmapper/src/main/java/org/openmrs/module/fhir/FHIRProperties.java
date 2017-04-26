package org.openmrs.module.fhir;

public class FHIRProperties {

    public static final String LOINC_SOURCE_NAME = "LOINC";

    public static final String FHIR_CONDITION_CODE_DIAGNOSIS = "diagnosis";
    public static final String FHIR_CONDITION_CODE_DIAGNOSIS_DISPLAY = "Diagnosis";

    public static final String FHIR_CONDITION_CODE_CHIEF_COMPLAINT = "complaint";
    public static final String FHIR_CONDITION_CODE_CHIEF_COMPLAINT_DISPLAY = "Complaint";

    public static final String UCUM_URL = "http://unitsofmeasure.org";

    public static final String UCUM_UNIT_FOR_YEARS = "a";

    public static final String FHIR_DOC_TYPECODES_URL = "http://hl7.org/fhir/vs/doc-typecodes";
    public static final String LOINC_CODE_DETAILS_NOTE = "51899-3";
    public static final String LOINC_DETAILS_NOTE_DISPLAY = "Details Document";

    public static final String FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL = "http://hl7.org/fhir/v2/0074";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE = "LAB";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_DISPLAY = "Laboratory";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE = "RAD";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_DISPLAY = "Radiology";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_CODE = "OTH";
    public static final String FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_DISPLAY = "Other";

    public static final String FHIR_YES_NO_INDICATOR_URL = "http://hl7.org/fhir/v2/0136";
    public static final String FHIR_YES_INDICATOR_CODE = "Y";
    public static final String FHIR_YES_INDICATOR_DISPLAY = "Yes";
    public static final String FHIR_NO_INDICATOR_CODE = "N";
    public static final String FHIR_NO_INDICATOR_DISPLAY = "No";
    public static final String RESOURCE_MAPPING_URL_FORMAT = "%s#%s/%s";

    public static String FHIR_DRUG_ORDER_AFTERNOON_DOSE_KEY = "afternoonDose";
    public static String FHIR_DRUG_ORDER_MORNING_DOSE_KEY = "morningDose";
    public static String FHIR_DRUG_ORDER_EVENING_DOSE_KEY = "eveningDose";

    public static final String FHIR_EXTENSION_URL = "https://sharedhealth.atlassian.net/wiki/display/docs/fhir-extensions";
    public static final String SCHEDULED_DATE_EXTENSION_NAME = "TimingScheduledDate";
    public static final String DOSAGEINSTRUCTION_CUSTOM_DOSAGE_EXTENSION_NAME = "DosageInstructionCustomDosage";
    public static final String MEDICATIONORDER_ACTION_EXTENSION_NAME = "MedicationOrderAction";
    public static final String PROCEDURE_REQUEST_PREVIOUS_REQUEST_EXTENSION_NAME = "PreviousProcedureRequest";
    public static final String PREVIOUS_CONDITION_EXTENSION_NAME = "PreviousCondition";
    public static final String DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME = "DiagnosticOrderCategory";

    public static final String FHIR_CONDITION_CATEGORY_URL = "http://hl7.org/fhir/condition-category";
    public static final String FHIR_CONDITION_CATEGORY_COMPLAINT_CODE = "complaint";
    public static final String FHIR_CONDITION_CATEGORY_DIAGNOSIS_CODE = "diagnosis";


    public static String getFhirExtensionUrl(String extensionName) {
        return FHIR_EXTENSION_URL + "#" + extensionName;
    }
}
