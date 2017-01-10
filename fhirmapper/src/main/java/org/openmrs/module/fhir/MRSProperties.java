package org.openmrs.module.fhir;

public class MRSProperties {
    public static final String MRS_OUT_PATIENT_VISIT_TYPE = "OPD";
    public static final String MRS_IN_PATIENT_VISIT_TYPE = "IPD";

    public static final String SHR_CLIENT_SYSTEM_NAME = "shrclientsystem";

    public static final String MRS_DIAGNOSIS_STATUS_PRESUMED = "Presumed";
    public static final String MRS_DIAGNOSIS_STATUS_CONFIRMED = "Confirmed";
    public static final String MRS_DIAGNOSIS_SEVERITY_PRIMARY = "Primary";
    public static final String MRS_CONCEPT_NAME_VISIT_DIAGNOSES = "Visit Diagnoses";
    public static final String MRS_CONCEPT_NAME_CODED_DIAGNOSIS = "Coded Diagnosis";
    public static final String MRS_CONCEPT_NAME_DIAGNOSIS_CERTAINTY = "Diagnosis Certainty";
    public static final String MRS_CONCEPT_NAME_DIAGNOSIS_ORDER = "Diagnosis order";
    public final static String MRS_CONCEPT_NAME_INITIAL_DIAGNOSIS = "Bahmni Initial Diagnosis";
    public final static String MRS_CONCEPT_NAME_DIAGNOSIS_STATUS = "Bahmni Diagnosis Status";
    public final static String MRS_CONCEPT_NAME_DIAGNOSIS_REVISED = "Bahmni Diagnosis Revised";

    public static final String MRS_CONCEPT_NAME_COMPLAINT_CONDITION_TEMPLATE = "Condition Complaint Template";
    public static final String MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DATA = "Condition Chief Complaint Data";
    public static final String MRS_CONCEPT_NAME_CHIEF_COMPLAINT = "Condition Chief Complaint";
    public static final String MRS_CONCEPT_NAME_NON_CODED_CHIEF_COMPLAINT = "Condition Non-Coded Chief Complaint";
    public static final String MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DURATION = "Condition Chief Complaint Duration";

    public static final String MRS_CONCEPT_NAME_FAMILY_HISTORY = "Family History Template";
    public static final String MRS_CONCEPT_NAME_PERSON = "Family Member History";
    public static final String MRS_CONCEPT_NAME_BORN_ON = "Family Member Born On";
    public static final String MRS_CONCEPT_NAME_RELATIONSHIP_CONDITION = "Family Member Condition";
    public static final String MRS_CONCEPT_NAME_ONSET_AGE = "Family Member Condition Onset Age";
    public static final String MRS_CONCEPT_NAME_RELATIONSHIP_NOTES = "Family Member Condition Notes";
    public static final String MRS_CONCEPT_NAME_RELATIONSHIP_DIAGNOSIS = "Family Member Condition Diagnosis";

    public static final String MRS_CONCEPT_IMMUNIZATION_INCIDENT_TEMPLATE = "Immunization Incident Template";
    public static final String MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP = "Immunization Incident Group";
    public static final String MRS_CONCEPT_IMMUNIZATION_NOTE = "Immunization Note";
    public static final String MRS_CONCEPT_VACCINATION_REFUSED = "Immunization Incident Vaccination Refused";
    public static final String MRS_CONCEPT_VACCINE = "Immunization Incident Vaccine";
    public static final String MRS_CONCEPT_VACCINATION_DATE = "Immunization Incident Vaccination Date";
    public static final String MRS_CONCEPT_VACCINATION_REPORTED = "Immunization Incident Vaccination Reported";
    public static final String MRS_CONCEPT_DOSAGE = "Immunization Incident Vaccination Dosage";

    public static final String MRS_CONCEPT_PROCEDURE_ORDER_FULFILLMENT_FORM = "Procedure Order Fulfillment Form";
    public static final String MRS_CONCEPT_PROCEDURES_TEMPLATE = "Procedure Template";
    public static final String MRS_CONCEPT_PROCEDURE_TYPE = "Procedure Type";
    public static final String MRS_CONCEPT_PROCEDURE_START_DATE = "Procedure Start Date";
    public static final String MRS_CONCEPT_PROCEDURE_END_DATE = "Procedure End Date";
    public static final String MRS_CONCEPT_PROCEDURE_DIAGNOSIS = "Procedure Diagnosis";
    public static final String MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_STUDY = "Procedure Diagnostic Study";
    public static final String MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_TEST = "Procedure Diagnostic Test";
    public static final String MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT = "Procedure Diagnostic Result";
    public static final String MRS_CONCEPT_PROCEDURE_NOTES = "Procedure Notes";
    public static final String MRS_CONCEPT_PROCEDURE_FOLLOWUP = "Procedure Followup";

    public static final String MRS_ORDER_FULFILLMENT_FORM_SUFFIX = " Fulfillment Form";

    public static final String MRS_CONCEPT_NAME_LAB_NOTES = "LAB_NOTES";

    public static final String MRS_CARE_SETTING_FOR_OUTPATIENT = "Outpatient";

    public static final String MRS_CARE_SETTING_FOR_INPATIENT = "Inpatient";

    public static final String MRS_ENC_TYPE_LAB_RESULT = "LAB_RESULT";
    public static final String MRS_CONCEPT_CLASS_LAB_SET = "LabSet";

    public static final String MRS_DRUG_ORDER_TYPE = "Drug Order";
    public static final String MRS_LAB_ORDER_TYPE = "Lab Order";
    public static final String MRS_RADIOLOGY_ORDER_TYPE = "Radiology Order";
    public static final String MRS_PROCEDURE_ORDER_TYPE = "Procedure Order";

    public static final String DRUG_ORDER_QUANTITY_UNITS_CONCEPT_NAME = "Unit(s)";
    public static final String TR_VALUESET_RELATIONSHIP_TYPE = "Relationship Type";
    public static final String TR_VALUESET_QUANTITY_UNITS = "Quantity Units";
    public static final String TR_VALUESET_MEDICATION_FORMS = "Medication Forms";
    public static final String TR_VALUESET_MEDICATION_PACKAGE_FORMS = "Medication Package Forms";
    public static final String TR_VALUESET_IMMUNIZATION_REASON = "Immunization Reason";
    public static final String TR_VALUESET_IMMUNIZATION_REFUSAL_REASON = "No Immunization Reason";
    public static final String TR_VALUESET_ROUTE_OF_ADMINSTRATION = "Route of Administration";
    public static final String TR_VALUESET_IMMUNIZATION_STATUS = "Immunization Status";
    public static final String TR_VALUESET_PROCEDURE_OUTCOME = "Procedure Outcome";
    public static final String TR_VALUESET_PROCEDURE_STATUS = "Procedure Status";

    public static final String TR_CONCEPT_CAUSE_OF_DEATH = "Cause of Death";
    public static final String TR_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH = "Unspecified cause of death";

    public static final String GLOBAL_PROPERTY_CONCEPT_RELATIONSHIP_TYPE = "shr.concept.relationshipType";
    public static final String GLOBAL_PROPERTY_CONCEPT_QUANTITY_UNITS = "shr.concept.quantityUnits";
    public static final String GLOBAL_PROPERTY_CONCEPT_MEDICATION_FORMS = "shr.concept.medicationForms";
    public static final String GLOBAL_PROPERTY_CONCEPT_MEDICATION_PACKAGE_FORMS = "shr.concept.medicationPackageForms";
    public static final String GLOBAL_PROPERTY_CONCEPT_IMMUNIZATION_REASON = "shr.concept.immunizationReason";
    public static final String GLOBAL_PROPERTY_CONCEPT_IMMUNIZATION_REFUSAL_REASON = "shr.concept.immunizationRefusalReason";
    public static final String GLOBAL_PROPERTY_CONCEPT_ROUTE_OF_ADMINISTRATION = "shr.concept.routeOfAdministration";
    public static final String GLOBAL_PROPERTY_CONCEPT_PROCEDURE_OUTCOME = "shr.concept.procedureOutcome";
    public static final String GLOBAL_PROPERTY_CONCEPT_PROCEDURE_STATUS = "shr.concept.procedureStatus";
    public static final String GLOBAL_PROPERTY_CONCEPT_IMMUNIZATION_STATUS = "shr.concept.immunizationStatus";

    public static final String GLOBAL_PROPERTY_IGNORED_CONCEPT_LIST = "shr.ignoreConceptList";
    public static final String GLOBAL_PROPERTY_ORDER_TYPE_TO_FHIR_CODE_MAPPINGS = "shr.orderTypeToFHIRCodeMappings";

    public static final String GLOBAL_PROPERTY_DOSING_FORMS_CONCEPT_UUID = "order.drugDosingUnitsConceptUuid";

    public static final String GLOBAL_PROPERTY_CONCEPT_CAUSE_OF_DEATH = "concept.causeOfDeath";
    public static final String GLOBAL_PROPERTY_CONCEPT_UNSPECIFIED_CAUSE_OF_DEATH = "concept.unspecifiedCauseOfDeath";

    public static final String GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID = "shr.hie.facilities.location.tag";
    public static final String GLOBAL_PROPERTY_SHR_LOGIN_LOCATION_TAG_ID = "shr.login.location.tag";
    public static final String GLOBAL_PROPERTY_SHR_SYSTEM_USER_TAG = "shr.system.user";
    public static final String GLOBAL_PROPERTY_SHR_CATEGORY_EVENT = "shr.system.shrCategoryForEncounterEvents";

    public static final String GLOBAL_PROPERTY_EMR_PRIMARY_IDENTIFIER_TYPE = "emr.primaryIdentifierType";
    public static final String GLOBAL_PROPERTY_DEFAULT_IDENTIFIER_TYPE_ID = "shr.defaultPatientIdentifierSourceId";
    public static final String GLOBAL_PROPERTY_FIELD_WORKER_ROLE_NAME = "user.fieldWorkerRoleName";

    public static final String BAHMNI_DRUG_ORDER_ADDITIONAL_INSTRCTIONS_KEY = "additionalInstructions";
    public static final String BAHMNI_DRUG_ORDER_INSTRCTIONS_KEY = "instructions";
    public static final String BAHMNI_DRUG_ORDER_AFTERNOON_DOSE_KEY = "afternoonDose";
    public static final String BAHMNI_DRUG_ORDER_MORNING_DOSE_KEY = "morningDose";
    public static final String BAHMNI_DRUG_ORDER_EVENING_DOSE_KEY = "eveningDose";

    public static final String UNVERIFIED_BY_TR = "(Unverified Term)";
    public static final String LOCAL_CONCEPT_VERSION_PREFIX = "DLN-H";
    public static final String CONCEPT_MAP_TYPE_MAY_BE_A = "MAY BE A";

    public static final String ENCOUNTER_UPDATE_VOID_REASON = "DLN:Encounter updated";

    public static final String RESOURCE_MAPPING_EXTERNAL_ID_FORMAT = "%s:%s";
    
    public static final String MRS_LOGIN_LOCATION_TAG_NAME = "Login Location";

    public static final String ORDER_DISCONTINUE_REASON = "Cancelled in SHR";
    public static final int ORDER_AUTO_EXPIRE_DURATION_MINUTES = 1440;
}