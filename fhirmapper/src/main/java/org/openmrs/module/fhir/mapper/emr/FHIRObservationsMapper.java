package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import org.openmrs.*;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.FHIREncounterUtil;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;

import static org.openmrs.module.fhir.MRSProperties.LOCAL_CONCEPT_VERSION_PREFIX;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.findResourceByReference;

@Component
public class FHIRObservationsMapper implements FHIRResourceMapper {
    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private FHIRObservationValueMapper resourceValueMapper;

    @Override
    public boolean canHandle(IResource resource) {
        return (resource instanceof Observation);
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Observation observation = (Observation) resource;
        Obs obs = mapObs(shrEncounterBundle, emrEncounter, observation);
        emrEncounter.addObs(obs);
    }

    public Obs mapObs(ShrEncounterBundle shrEncounterBundle, EmrEncounter emrEncounter, Observation observation) {
        String facilityId = FHIREncounterUtil.getFacilityId(shrEncounterBundle.getBundle());
        Concept concept = mapConcept(observation, facilityId);
        if (concept == null) return null;
        Obs result = new Obs();
        result.setConcept(concept);
        try {
            mapValue(observation, concept, result);
            mapRelatedObservations(shrEncounterBundle, observation, result, emrEncounter);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void mapValue(Observation observation, Concept concept, Obs result) throws ParseException {
        if (isLocallyCreatedConcept(concept)) {
            mapValueAsString(observation, result);
        } else {
            IDatatype value = observation.getValue();
            resourceValueMapper.map(value, result);
        }
    }

    private void mapRelatedObservations(ShrEncounterBundle encounterComposition, Observation observation, Obs obs, EmrEncounter emrEncounter) throws ParseException {
        for (Observation.Related component : observation.getRelated()) {
            Obs member;
            Observation relatedObs = (Observation) findResourceByReference(encounterComposition.getBundle(), component.getTarget());
            member = mapObs(encounterComposition, emrEncounter, relatedObs);
            if (member != null) {
                obs.addGroupMember(member);
            }
        }
    }

    private boolean isLocallyCreatedConcept(Concept concept) {
        return concept.getVersion() != null && concept.getVersion().startsWith(LOCAL_CONCEPT_VERSION_PREFIX);
    }

    private void mapValueAsString(Observation relatedObs, Obs result) throws ParseException {
        IDatatype value = relatedObs.getValue();
        if (value != null)
            result.setValueAsString(ObservationValueConverter.convertToText(value));
    }

    private Concept mapConcept(Observation observation, String facilityId) {
        CodeableConceptDt observationName = observation.getCode();
        if (observationName.getCoding() != null && observationName.getCoding().isEmpty()) {
            return null;
        }
        return omrsConceptLookup.findOrCreateLocalConceptByCodings(observationName.getCoding(), facilityId, ConceptClass.MISC_UUID, ConceptDatatype.TEXT_UUID);
    }
}
