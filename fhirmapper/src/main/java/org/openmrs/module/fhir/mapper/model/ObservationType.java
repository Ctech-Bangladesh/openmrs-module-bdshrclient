package org.openmrs.module.fhir.mapper.model;

import static org.openmrs.module.fhir.MRSProperties.*;

public enum ObservationType {

    COMPLAINT_CONDITION_TEMPLATE(MRS_CONCEPT_NAME_COMPLAINT_CONDITION_TEMPLATE),
    CHIEF_COMPLAINT_DATA(MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DATA),
    VISIT_DIAGNOSES(MRS_CONCEPT_NAME_VISIT_DIAGNOSES),
    FAMILY_HISTORY(MRS_CONCEPT_NAME_FAMILY_HISTORY),
    IMMUNIZATION(MRS_CONCEPT_IMMUNIZATION_INCIDENT_TEMPLATE),
    PROCEDURES(MRS_CONCEPT_PROCEDURES_TEMPLATE),
    PROCEDURE_FULFILLMENT(MRS_CONCEPT_PROCEDURE_ORDER_FULFILLMENT_FORM);
    private String displayName;

    ObservationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
