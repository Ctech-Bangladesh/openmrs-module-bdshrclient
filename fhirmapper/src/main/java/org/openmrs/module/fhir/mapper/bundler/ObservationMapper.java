package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Type;
import org.openmrs.Obs;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.mapper.model.RelatedObservation;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.mapper.model.ObservationType.*;

@Component("FHIRObservationMapper")
public class ObservationMapper implements EmrObsResourceHandler {

    private ObservationValueMapper observationValueMapper;
    private final CodeableConceptService codeableConceptService;
    private GlobalPropertyLookUpService globalPropertyLookUpService;
    private ObservationBuilder observationBuilder;

    @Autowired
    public ObservationMapper(ObservationValueMapper observationValueMapper, CodeableConceptService codeableConceptService, GlobalPropertyLookUpService globalPropertyLookUpService, ObservationBuilder observationBuilder) {
        this.observationValueMapper = observationValueMapper;
        this.codeableConceptService = codeableConceptService;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
        this.observationBuilder = observationBuilder;
    }

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        if (isNotOfKnownTypes(obs)) {
            return false;
        }
        return observation.getOrder() == null;
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> result = new ArrayList<>();
        FHIRResource rootObservationEntry = mapObs(obs, fhirEncounter, null, result, systemProperties);
        if (rootObservationEntry != null) {
            removeObservationsHierarchyWithoutValues(result, rootObservationEntry);
        }

        return result;
    }

    public FHIRResource mapObs(Obs obs, FHIREncounter fhirEncounter, Observation parentObservation, List<FHIRResource> result, SystemProperties systemProperties) {
        FHIRResource entry = mapObsToFhirResource(obs, fhirEncounter, systemProperties);
        if (entry == null) return null;
        Observation observation = (Observation) entry.getResource();
        if (parentObservation != null)
            mapRelatedObservation(observation).mergeWith(parentObservation, systemProperties);
        for (Obs member : obs.getGroupMembers()) {
            mapObs(member, fhirEncounter, observation, result, systemProperties);
        }
        result.add(entry);
        return entry;
    }

    private boolean isNotOfKnownTypes(CompoundObservation obs) {
        return obs.isOfType(COMPLAINT_CONDITION_TEMPLATE)
                || obs.isOfType(VISIT_DIAGNOSES)
                || obs.isOfType(FAMILY_HISTORY)
                || obs.isOfType(IMMUNIZATION)
                || obs.isOfType(PROCEDURES);
    }

    private boolean hasIgnoredConcept(Obs obs) {
        String globalPropertyValue = globalPropertyLookUpService.getGlobalPropertyValue(MRSProperties.GLOBAL_PROPERTY_IGNORED_CONCEPT_LIST);
        if (StringUtils.isBlank(globalPropertyValue)) return false;
        List<String> conceptIds = asList(StringUtils.split(globalPropertyValue, ","));
        for (String conceptId : conceptIds) {
            if (obs.getConcept().getId().equals(Integer.parseInt(conceptId.trim()))) {
                return true;
            }
        }
        return false;
    }

    private FHIRResource mapObsToFhirResource(Obs openmrsObs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        if (hasIgnoredConcept(openmrsObs)) return null;
        FHIRResource fhirObservationResource = observationBuilder.buildObservationResource(fhirEncounter, openmrsObs.getUuid(), openmrsObs.getConcept().getName().getName(), systemProperties);
        Observation fhirObservation = (Observation) fhirObservationResource.getResource();
        fhirObservation.setStatus(Observation.ObservationStatus.PRELIMINARY);
        mapCode(openmrsObs, fhirObservation);
        mapValue(openmrsObs, fhirObservation);
        return fhirObservationResource;
    }

    private void mapValue(Obs openmrsObs, Observation fhirObservation) {
        Type value = observationValueMapper.map(openmrsObs);
        if (null != value) {
            fhirObservation.setValue(value);
        }
    }

    private void mapCode(Obs openmrsObs, Observation fhirObservation) {
        CodeableConcept name = buildCode(openmrsObs);
        if (null != name) {
            fhirObservation.setCode(name);
        }
    }

    private CodeableConcept buildCode(Obs observation) {
        if (null == observation.getConcept()) {
            return null;
        }
        return codeableConceptService.addTRCodingOrDisplay(observation.getConcept());
    }

    private RelatedObservation mapRelatedObservation(Observation observation) {
        return new RelatedObservation(observation);
    }

    private boolean removeObservationsHierarchyWithoutValues(List<FHIRResource> result, FHIRResource observationResource) {
        boolean shouldRemove = true;
        ArrayList<Observation.ObservationRelatedComponent> childrenToRemove = new ArrayList<>();
        Observation observation = (Observation) observationResource.getResource();
        if (CollectionUtils.isEmpty(observation.getRelated())) {
            shouldRemove = (observation.getValue() == null);
        }

        for (Observation.ObservationRelatedComponent related : observation.getRelated()) {
            Reference target = related.getTarget();
            FHIRResource targetObservation = findObservationById(target, result);
            boolean withoutValuesAndChild = removeObservationsHierarchyWithoutValues(result, targetObservation);
            if (!withoutValuesAndChild) {
                shouldRemove = false;
            } else {
                childrenToRemove.add(related);
            }
        }

        List<Observation.ObservationRelatedComponent> relatedList = observation.getRelated();
        relatedList.removeAll(childrenToRemove);
        observation.setRelated(new ArrayList<>(relatedList));
        if (shouldRemove) {
            result.remove(observationResource);
        }
        return shouldRemove;
    }

    private FHIRResource findObservationById(Reference target, List<FHIRResource> result) {
        for (FHIRResource fhirResource : result) {
            if (fhirResource.getResource().getId().equals(target.getReference())) {
                return fhirResource;
            }
        }
        return null;
    }
}
