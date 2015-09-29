package org.openmrs.module.fhir.utils;

import org.openmrs.module.shrclient.util.SystemProperties;

import static org.openmrs.module.fhir.mapper.MRSProperties.*;

public enum TrValueSetType {
    RELATIONSHIP_TYPE(TR_VALUESET_RELATIONSHIP_TYPE, GLOBAL_PROPERTY_CONCEPT_RELATIONSHIP_TYPE, null),
    QUANTITY_UNITS(TR_VALUESET_QUANTITY_UNITS, GLOBAL_PROPERTY_CONCEPT_QUANTITY_UNITS, PropertyKeyConstants.TR_VALUESET_QUANTITY_UNITS),
    IMMUNIZATION_REASON(TR_VALUESET_IMMUNIZATION_REASON, GLOBAL_PROPERTY_CONCEPT_IMMUNIZATION_REASON, PropertyKeyConstants.TR_VALUESET_IMMUNIZATION_REASON),
    IMMUNIZATION_REFUSAL_REASON(TR_VALUESET_IMMUNIZATION_REFUSAL_REASON, GLOBAL_PROPERTY_CONCEPT_IMMUNIZATION_REFUSAL_REASON, PropertyKeyConstants.TR_VALUESET_IMMUNIZATION_REFUSAL_REASON),
    ROUTE_OF_ADMINISTRATION(TR_VALUESET_ROUTE_OF_ADMINSTRATION, GLOBAL_PROPERTY_CONCEPT_ROUTE_OF_ADMINISTRATION, PropertyKeyConstants.TR_VALUESET_ROUTE),
    PROCEDURE_OUTCOME(TR_VALUESET_PROCEDURE_OUTCOME, GLOBAL_PROPERTY_CONCEPT_PROCEDURE_OUTCOME, PropertyKeyConstants.TR_VALUESET_PROCEDURE_OUTCOME),
    PROCEDURE_FOLLOWUP(TR_VALUESET_PROCEDURE_FOLLOWUP, GLOBAL_PROPERTY_CONCEPT_PROCEDURE_FOLLOWUP, PropertyKeyConstants.TR_VALUESET_PROCEDURE_FOLLOWUP);

    private final String defaultConceptName;
    private final String globalPropertyKey;
    private final String trPropertyValueKey;

    TrValueSetType(String defaultConceptName, String globalPropertyKey, String trPropertyValueKey) {
        this.defaultConceptName = defaultConceptName;
        this.globalPropertyKey = globalPropertyKey;
        this.trPropertyValueKey = trPropertyValueKey;
    }

    public String getDefaultConceptName() {
        return defaultConceptName;
    }

    public String getGlobalPropertyKey() {
        return globalPropertyKey;
    }

    public String getTrPropertyValueSetUrl(SystemProperties systemProperties) {
        return systemProperties.getTrValuesetUrl(trPropertyValueKey);
    }
}
