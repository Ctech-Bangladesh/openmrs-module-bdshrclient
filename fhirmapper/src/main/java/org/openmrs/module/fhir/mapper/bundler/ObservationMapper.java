package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Obs;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.bundler.condition.ObservationValueMapper;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.RelatedObservation;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.MRSProperties.MRS_ENC_TYPE_LAB_RESULT;
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
        String encounterType = observation.getEncounter().getEncounterType().getName();
        return !MRS_ENC_TYPE_LAB_RESULT.equals(encounterType);
    }

    private boolean isNotOfKnownTypes(CompoundObservation obs) {
        return obs.isOfType(COMPLAINT_CONDITION_TEMPLATE)
                || obs.isOfType(VISIT_DIAGNOSES)
                || obs.isOfType(FAMILY_HISTORY)
                || obs.isOfType(IMMUNIZATION)
                || obs.isOfType(PROCEDURES);
    }

    @Override
    public List<FHIRResource> map(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> result = new ArrayList<>();

        if (null != obs && !hasIgnoredConcept(obs)) {
            result = mapToFhirObservation(obs, fhirEncounter, systemProperties);
        }
        return result;
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

    public List<FHIRResource> mapToFhirObservation(Obs observation, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> result = new ArrayList<>();
        FHIRResource entry = mapObservation(observation, fhirEncounter, systemProperties);
        Observation fhirObservation = (Observation) entry.getResource();
        fhirObservation.setStatus(ObservationStatusEnum.PRELIMINARY);
        for (Obs member : observation.getGroupMembers()) {
            mapGroupMember(member, fhirEncounter, fhirObservation, result, systemProperties);
        }
        result.add(entry);
        return result;
    }

    private FHIRResource mapObservation(Obs openmrsObs, Encounter fhirEncounter, SystemProperties systemProperties) {
        FHIRResource fhirObservationResource = observationBuilder.buildObservationResource(fhirEncounter, systemProperties, openmrsObs.getUuid(), openmrsObs.getConcept().getName().getName());
        Observation fhirObservation = (Observation) fhirObservationResource.getResource();
        fhirObservation.setStatus(ObservationStatusEnum.PRELIMINARY);
        mapCode(openmrsObs, fhirObservation);
        mapValue(openmrsObs, fhirObservation);
        return fhirObservationResource;
    }

    private void mapGroupMember(Obs obs, Encounter fhirEncounter, Observation parentObservation, List<FHIRResource> result, SystemProperties systemProperties) {
        if (hasIgnoredConcept(obs)) {
            return;
        }
        FHIRResource entry = mapObservation(obs, fhirEncounter, systemProperties);
        Observation observation = (Observation) entry.getResource();
        mapRelatedObservation(observation).mergeWith(parentObservation, systemProperties);
        for (Obs member : obs.getGroupMembers()) {
            mapGroupMember(member, fhirEncounter, observation, result, systemProperties);
        }
        result.add(entry);
    }

    private void mapValue(Obs openmrsObs, Observation fhirObservation) {
        IDatatype value = observationValueMapper.map(openmrsObs);
        if (null != value) {
            fhirObservation.setValue(value);
        }
    }

    private void mapCode(Obs openmrsObs, Observation fhirObservation) {
        CodeableConceptDt name = buildCode(openmrsObs);
        if (null != name) {
            fhirObservation.setCode(name);
        }
    }

    private CodeableConceptDt buildCode(Obs observation) {
        if (null == observation.getConcept()) {
            return null;
        }
        return codeableConceptService.addTRCodingOrDisplay(observation.getConcept());
    }

    private RelatedObservation mapRelatedObservation(Observation observation) {
        return new RelatedObservation(observation);
    }
}
