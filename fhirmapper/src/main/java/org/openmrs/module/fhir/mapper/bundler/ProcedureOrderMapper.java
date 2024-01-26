package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.SUSPENDED;
import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.module.fhir.MRSProperties.MRS_PROCEDURE_ORDER_TYPE;
import static org.openmrs.module.fhir.MRSProperties.TR_ORDER_TYPE_PROCEDURE_CODE;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component
public class ProcedureOrderMapper implements EmrOrderResourceHandler {
    private final static String PROCEDURE_REQUEST_RESOURCE_DISPLAY = "Procedure Request";

    private ProviderLookupService providerLookupService;
    private CodeableConceptService codeableConceptService;
    private ProcedureRequestBuilder procedureRequestBuilder;

    @Autowired
    public ProcedureOrderMapper(ProviderLookupService providerLookupService, CodeableConceptService codeableConceptService, ProcedureRequestBuilder procedureRequestBuilder) {
        this.providerLookupService = providerLookupService;
        this.codeableConceptService = codeableConceptService;
        this.procedureRequestBuilder = procedureRequestBuilder;
    }

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_PROCEDURE_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;
        List<FHIRResource> resources = new ArrayList<>();
        FHIRResource fhirResource = createProcedureRequestResource(order, fhirEncounter, systemProperties);
        if (null != fhirResource) {
            resources.add(fhirResource);
            resources.add(createProvenance(PROCEDURE_REQUEST_RESOURCE_DISPLAY, order.getDateActivated(), getOrdererReference(order, fhirEncounter), fhirResource.getResource().getId()));
        }
        return resources;
    }

    public FHIRResource createProcedureRequestResource(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        CodeableConcept code = findCodeForOrder(order);
        if (code == null) {
            return null;
        }
        boolean isDiscontinuedOrder = Order.Action.DISCONTINUE.equals(order.getAction());
        String orderUuid = order.getUuid();
        if (isDiscontinuedOrder) {
            orderUuid = order.getPreviousOrder().getUuid();
        }

        ProcedureRequest procedureRequest = procedureRequestBuilder.createProcedureRequest(order, fhirEncounter, systemProperties,
                TR_ORDER_TYPE_PROCEDURE_CODE, orderUuid);
        procedureRequest.setAuthoredOn(order.getDateActivated());
        setOrderStatus(order, procedureRequest);
        addNotes(order, procedureRequest);
        procedureRequest.setCode(code);
        return new FHIRResource(PROCEDURE_REQUEST_RESOURCE_DISPLAY, procedureRequest.getIdentifier(), procedureRequest);
    }


    private void setOrderStatus(Order order, ProcedureRequest procedureRequest) {
        procedureRequest.setStatus(order.getAction().equals(DISCONTINUE) ? SUSPENDED : ACTIVE);
    }

    private void addNotes(Order order, ProcedureRequest procedureRequest) {
        Annotation notes = new Annotation();
        notes.setText(order.getCommentToFulfiller());
        procedureRequest.addNote(notes);
    }

    private Reference getOrdererReference(Order order, FHIREncounter fhirEncounter) {
        if (order.getOrderer() != null) {
            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
            if (providerUrl != null) {
                return new Reference().setReference(providerUrl);
            }
        }
        return fhirEncounter.getFirstParticipantReference();
    }

    private CodeableConcept findCodeForOrder(Order order) {
        if (null == order.getConcept()) {
            return null;
        }
        return codeableConceptService.addTRCodingOrDisplay(order.getConcept());
    }
}
