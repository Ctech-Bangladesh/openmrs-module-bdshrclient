package org.openmrs.module.fhir.mapper.model;

import org.hl7.fhir.dstu3.model.Observation;
import org.openmrs.module.shrclient.util.SystemProperties;

public interface PartOf<Aggregate> {

    public Aggregate mergeWith(Observation observation, SystemProperties systemProperties);
}
