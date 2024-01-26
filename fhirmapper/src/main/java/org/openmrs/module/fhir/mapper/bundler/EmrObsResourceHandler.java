package org.openmrs.module.fhir.mapper.bundler;


import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.util.List;

public interface EmrObsResourceHandler {
    boolean canHandle(Obs observation);

    List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties);

}
