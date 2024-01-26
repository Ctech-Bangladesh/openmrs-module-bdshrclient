package org.openmrs.module.fhir;

import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;

public class ObsHelper {
    public Obs findMemberObsByConceptName(Obs observation, String conceptName) {
        CompoundObservation compoundObservation = new CompoundObservation(observation);
        return compoundObservation.getMemberObsForConceptName(conceptName);
    }
}
