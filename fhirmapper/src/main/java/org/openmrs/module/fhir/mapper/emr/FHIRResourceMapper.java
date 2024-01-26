package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.stereotype.Component;

@Component
public interface FHIRResourceMapper {
    public boolean canHandle(Resource resource);

    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties);
}
