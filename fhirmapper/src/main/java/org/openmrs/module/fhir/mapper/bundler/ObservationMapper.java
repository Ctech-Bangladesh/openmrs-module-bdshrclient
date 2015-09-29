package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.valueset.ObservationReliabilityEnum;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.bundler.condition.ObservationValueMapper;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.RelatedObservation;
import org.openmrs.module.fhir.utils.CodableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_ENC_TYPE_LAB_RESULT;
import static org.openmrs.module.fhir.mapper.model.ObservationType.*;

@Component("FHIRObservationMapper")
public class ObservationMapper implements EmrObsResourceHandler {

    private ObservationValueMapper observationValueMapper;
    private IdMappingsRepository idMappingsRepository;
    private final CodableConceptService codableConceptService;

    @Autowired
    public ObservationMapper(ObservationValueMapper observationValueMapper, IdMappingsRepository idMappingsRepository, CodableConceptService codableConceptService, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.observationValueMapper = observationValueMapper;
        this.idMappingsRepository = idMappingsRepository;
        this.codableConceptService = codableConceptService;
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
        if (null != obs) {
            result = mapToFhirObservation(obs, fhirEncounter, systemProperties);
        }
        return result;
    }

    public List<FHIRResource> mapToFhirObservation(Obs observation, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> result = new ArrayList<>();
        FHIRResource entry = mapObservation(observation, fhirEncounter, systemProperties);
        for (Obs member : observation.getGroupMembers()) {
            mapGroupMember(member, fhirEncounter, (Observation) entry.getResource(), result, systemProperties);
        }
        result.add(entry);
        return result;
    }

    public FHIRResource mapObservation(Obs openmrsObs, Encounter fhirEncounter, SystemProperties systemProperties) {
        Observation fhirObservation = buildObservationResource(openmrsObs, fhirEncounter, systemProperties);
        mapCode(openmrsObs, fhirObservation);
        mapValue(openmrsObs, fhirObservation);
        return buildFhirResource(fhirObservation, openmrsObs);
    }

    private Observation buildObservationResource(Obs openmrsObs, Encounter fhirEncounter, SystemProperties systemProperties) {
        Observation fhirObservation = new Observation();
        fhirObservation.setSubject(fhirEncounter.getPatient());
        fhirObservation.setEncounter(new ResourceReferenceDt().setReference(fhirEncounter.getId().getValue()));
        fhirObservation.setStatus(ObservationStatusEnum.FINAL);
        fhirObservation.setReliability(ObservationReliabilityEnum.OK);
        String id = new EntityReference().build(IResource.class, systemProperties, openmrsObs.getUuid());
        fhirObservation.setId(id);
        fhirObservation.addIdentifier(new IdentifierDt().setValue(id));
        return fhirObservation;
    }

    private FHIRResource buildFhirResource(Observation observation, Obs obs) {
        return new FHIRResource(obs.getConcept().getName().getName(), observation.getIdentifier(), observation);
    }

    private void mapGroupMember(Obs obs, Encounter fhirEncounter, Observation parentObservation, List<FHIRResource> result, SystemProperties systemProperties) {
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
        CodeableConceptDt observationName = codableConceptService.addTRCoding(observation.getConcept(), idMappingsRepository);
        if (CollectionUtils.isEmpty(observationName.getCoding())) {
            CodingDt coding = observationName.addCoding();
            coding.setDisplay(observation.getConcept().getName().getName());
        }
        return observationName;
    }

    private RelatedObservation mapRelatedObservation(Observation observation) {
        return new RelatedObservation(observation);
    }
}
