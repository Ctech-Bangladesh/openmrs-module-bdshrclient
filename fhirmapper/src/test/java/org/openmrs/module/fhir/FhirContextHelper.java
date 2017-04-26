package org.openmrs.module.fhir;

import ca.uhn.fhir.context.FhirContext;

public class FhirContextHelper {
    private static FhirContext fhirContext = FhirContext.forDstu3();

    public static FhirContext getFhirContext() {
        return fhirContext;
    }
}
