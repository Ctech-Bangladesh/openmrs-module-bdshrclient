package org.openmrs.module.shrclient.util;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.stereotype.Component;

@Component
public class FhirBundleContextHolder {
    private static FhirContext fhirContext;


    public static FhirContext getFhirContext() {
        if(fhirContext == null) {
            fhirContext = FhirContext.forDstu3();
        }
        return fhirContext;
    }
}
