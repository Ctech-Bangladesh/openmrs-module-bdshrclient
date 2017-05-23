package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Procedure;
import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.*;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.MRSProperties.MRS_CONCEPT_PROCEDURES_TEMPLATE;

@Component
public class ProcedureFulfillmentMapper implements EmrObsResourceHandler {
    @Autowired
    private ProcedureMapper procedureMapper;
    @Autowired
    private IdMappingRepository idMappingRepository;

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation procedureFulfillmentObs = new CompoundObservation(observation);
        if (!procedureFulfillmentObs.isOfType(ObservationType.PROCEDURE_FULFILLMENT)) return false;
        Obs procedureTemplate = procedureFulfillmentObs.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURES_TEMPLATE);
        return (null != procedureTemplate);
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        CompoundObservation procedureFulfillmentObs = new CompoundObservation(obs);
        Obs procedureTemplate = procedureFulfillmentObs.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURES_TEMPLATE);
        List<FHIRResource> resources = procedureMapper.map(procedureTemplate, fhirEncounter, systemProperties);
        Procedure procedure = getProcedureForObs(resources);
        if (null == procedure) return Collections.emptyList();
        setIdentifier(obs, systemProperties, procedure);
        setRequest(obs, procedure);
        return resources;

    }

    private Procedure getProcedureForObs(List<FHIRResource> resources) {
        for (FHIRResource iResource : resources) {
            Resource resource = iResource.getResource();
            if (resource instanceof Procedure) return (Procedure) resource;
        }
        return null;
    }

    public void setRequest(Obs obs, Procedure procedure) {
        Order order = obs.getOrder();
        IdMapping idMapping = idMappingRepository.findByInternalId(order.getUuid(), IdMappingType.PROCEDURE_REQUEST);
        if (idMapping != null) {
            procedure.addBasedOn().setReference(idMapping.getUri());
        }
    }

    public void setIdentifier(Obs obs, SystemProperties systemProperties, Procedure procedure) {
        String id = new EntityReference().build(Obs.class, systemProperties, obs.getUuid());
        Identifier identifierDt = procedure.addIdentifier();
        identifierDt.setValue(id);
        procedure.setId(id);
        procedure.setIdentifier(asList(identifierDt));
    }
}
