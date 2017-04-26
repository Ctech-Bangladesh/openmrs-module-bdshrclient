package org.openmrs.module.fhir.mapper.model;

import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.module.shrclient.util.SystemProperties;

public class RelatedObservation implements PartOf<Observation> {

    private Observation relatedObservation;

    public RelatedObservation(Observation relatedObservation) {
        this.relatedObservation = relatedObservation;
    }


    @Override
    public Observation mergeWith(Observation observation, SystemProperties systemProperties) {
        Observation.ObservationRelatedComponent related = observation.addRelated();
        related.setTarget(new Reference().setReference(relatedObservation.getId()));
        related.setType(Observation.ObservationRelationshipType.HASMEMBER);
        return observation;
    }
}
