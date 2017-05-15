package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Order;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.module.fhir.MRSProperties.MRS_PROCEDURE_ORDER_TYPE;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component
public class ProcedureOrderMapper implements EmrOrderResourceHandler {
    private final static String PROCEDURE_REQUEST_RESOURCE_DISPLAY = "Procedure Request";
    @Autowired
    private ProviderLookupService providerLookupService;
    @Autowired
    private CodeableConceptService codeableConceptService;

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_PROCEDURE_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        List<FHIRResource> resources = new ArrayList<>();
        FHIRResource fhirResource = createProcedureRequestResource(order, fhirEncounter, systemProperties);
        if (null != fhirResource) {
            resources.add(fhirResource);
            resources.add(createProvenance(order.getDateActivated(), getOrdererReference(order, fhirEncounter), fhirResource.getResource().getId()));
        }
        return resources;
    }

    public FHIRResource createProcedureRequestResource(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        ProcedureRequest procedureRequest = new ProcedureRequest();
        procedureRequest.setSubject(fhirEncounter.getPatient());

        ProcedureRequest.ProcedureRequestRequesterComponent requester = new ProcedureRequest.ProcedureRequestRequesterComponent();
        requester.setAgent(getOrdererReference(order, fhirEncounter));
        procedureRequest.setRequester(requester);

        procedureRequest.setAuthoredOn(order.getDateActivated());
        String id = new EntityReference().build(Order.class, systemProperties, order.getUuid());
        procedureRequest.addIdentifier().setValue(id);
        procedureRequest.setId(id);
        procedureRequest.setContext(new Reference().setReference(fhirEncounter.getId()));
        procedureRequest.setIntent(ProcedureRequest.ProcedureRequestIntent.ORDER);

        setOrderStatus(order, procedureRequest);
        setHistory(order, procedureRequest, systemProperties);
        addCAtegory(procedureRequest, systemProperties);
        addNotes(order, procedureRequest);
        CodeableConcept code = findCodeForOrder(order);
        if (code == null) {
            return null;
        }
        procedureRequest.setCode(code);
        return new FHIRResource(PROCEDURE_REQUEST_RESOURCE_DISPLAY, procedureRequest.getIdentifier(), procedureRequest);
    }

    private void addCAtegory(ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        Coding coding = procedureRequest.addCategory().addCoding();
        coding.setCode(MRSProperties.TR_ORDER_TYPE_PROCEDURE_CODE);
        String trValuesetUrl = systemProperties.createValueSetUrlFor("order-type");
        coding.setSystem(trValuesetUrl);
    }

    private void setHistory(Order order, ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        Order previousOrder = order.getPreviousOrder();
        if (null == previousOrder) return;
        String previousOrderUuid = previousOrder.getUuid();
        String previousOrderUri = new EntityReference().build(Order.class, systemProperties, previousOrderUuid);
        Reference reference = procedureRequest.addRelevantHistory();
        reference.setReference(previousOrderUri + "-provenance4");
    }

    private void setOrderStatus(Order order, ProcedureRequest procedureRequest) {
        if (order.getAction().equals(DISCONTINUE))
            procedureRequest.setStatus(ProcedureRequest.ProcedureRequestStatus.SUSPENDED);
        else
            procedureRequest.setStatus(ProcedureRequest.ProcedureRequestStatus.ACTIVE);
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
