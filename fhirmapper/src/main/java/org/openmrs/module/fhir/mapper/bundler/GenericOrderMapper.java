package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.CANCELLED;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component("fhirRadiologyOrderMapper")
public class GenericOrderMapper implements EmrOrderResourceHandler {

    private final CodeableConceptService codeableConceptService;
    private final ProcedureRequestBuilder procedureRequestBuilder;
    private final GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    public GenericOrderMapper(CodeableConceptService codeableConceptService, ProcedureRequestBuilder procedureRequestBuilder, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.codeableConceptService = codeableConceptService;
        this.procedureRequestBuilder = procedureRequestBuilder;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    @Override
    public boolean canHandle(Order order) {
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
            if (order.getOrderType().getName().equals(openMRSOrderTypeMap.getType()))
                return true;
        }
        return false;
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;

        List<FHIRResource> fhirResources = new ArrayList<>();
        boolean isDiscontinuedOrder = Order.Action.DISCONTINUE.equals(order.getAction());
        String orderUuid = order.getUuid();
        if (isDiscontinuedOrder) {
            orderUuid = order.getPreviousOrder().getUuid();
        }
        ProcedureRequest procedureRequest = procedureRequestBuilder.createProcedureRequest(order, fhirEncounter,
                systemProperties, getCategoryFromOrder(order), orderUuid);

        procedureRequest.setCode(codeableConceptService.addTRCodingOrDisplay(order.getConcept()));
        procedureRequest.setStatus(isDiscontinuedOrder ? CANCELLED : ACTIVE);
        procedureRequest.setAuthoredOn(order.getDateActivated());

        if (isDiscontinuedOrder) {
            setHistory(order, procedureRequest, systemProperties);
        }

        FHIRResource fhirProcedureRequest = new FHIRResource("Procedure Request", procedureRequest.getIdentifier(), procedureRequest);
        fhirResources.add(fhirProcedureRequest);

        FHIRResource provenance = createProvenance(order.getDateActivated(), procedureRequest.getRequester().getAgent(), fhirProcedureRequest.getResource().getId());
        fhirResources.add(provenance);
        return fhirResources;
    }

    private String getCategoryFromOrder(Order order) {
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        OpenMRSOrderTypeMap typeMap = configuredOrderTypes.stream().filter(
                orderTypeMap -> order.getOrderType().getName().equals(orderTypeMap.getType())
        ).findFirst().orElse(null);

        return typeMap == null ? null : typeMap.getCode();
    }

    private void setHistory(Order order, ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        Order previousOrder = order.getPreviousOrder();
        if (null == previousOrder) return;
        String previousOrderUuid = previousOrder.getUuid();
        String previousOrderUri = new EntityReference().build(Order.class, systemProperties, previousOrderUuid);
        Reference reference = procedureRequest.addRelevantHistory();
        reference.setReference(buildProvenanceReference(previousOrderUri));
    }

    private String buildProvenanceReference(String resourceEntryUri) {
        return resourceEntryUri + "-provenance";
    }
}
