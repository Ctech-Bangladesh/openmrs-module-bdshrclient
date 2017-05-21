package org.openmrs.module.shrclient.model;


public class IdMappingType {
    // todo: Change procedure Order to Procedure Request, delete Diagnostic Order and migrate data
    // todo: Change medication order to medication Request, delete Diagnostic Order and migrate data
    public static final String ENCOUNTER = "encounter";
    public static final String CONCEPT_REFERENCE_TERM = "concept_reference_term";
    public static final String CONCEPT = "concept";
    public static final String MEDICATION = "Medication";
    public static final String PATIENT = "patient";
    public static final String FACILITY = "fr_location";
    public static final String PERSON_RELATION = "PERSON_RELATION";
    public static final String MEDICATION_ORDER = "MedicationOrder";
    public static final String ORDER = "Order";
    public static final String DIAGNOSIS = "Diagnosis";
    public static final String PROCEDURE_ORDER = "ProcedureOrder";
    public static final String DIAGNOSTIC_ORDER = "DiagnosticOrder";
    public static final String PROVIDER = "provider";
}
