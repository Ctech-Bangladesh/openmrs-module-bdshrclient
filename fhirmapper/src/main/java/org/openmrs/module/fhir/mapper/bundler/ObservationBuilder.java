package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IResource;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.stereotype.Component;

@Component("observationBuilder")
public class ObservationBuilder {
    public FHIRResource buildObservationResource(FHIREncounter fhirEncounter, String resourceId, String resourceName, SystemProperties systemProperties) {
        Observation fhirObservation = new Observation();
        fhirObservation.setSubject(fhirEncounter.getPatient());
        fhirObservation.setContext(new Reference().setReference(fhirEncounter.getId()));
        String id = new EntityReference().build(IResource.class, systemProperties, resourceId);
        fhirObservation.setId(id);
        fhirObservation.addIdentifier(new Identifier().setValue(id));
        fhirObservation.setPerformer(fhirEncounter.getParticipantReferences());
        return buildFhirResource(fhirObservation, resourceName);
    }

    private FHIRResource buildFhirResource(Observation observation, String resourceName) {
        return new FHIRResource(resourceName, observation.getIdentifier(), observation);
    }
}
