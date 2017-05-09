package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticOrderBuilder {
    @Autowired
    private ProviderLookupService providerLookupService;

    public ProcedureRequest createDiagnosticOrder(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        ProcedureRequest diagnosticOrder = new ProcedureRequest();
        diagnosticOrder.setSubject(fhirEncounter.getPatient());
        diagnosticOrder.setRequester(new ProcedureRequest.ProcedureRequestRequesterComponent().setAgent(getOrdererReference(order, fhirEncounter)));
        String orderUuid = order.getUuid();
        if (isDiscontinuedOrder(order))
            orderUuid = order.getPreviousOrder().getUuid();
        setDiagnosticOrderId(systemProperties, diagnosticOrder, orderUuid);
        diagnosticOrder.setContext(new Reference().setReference(fhirEncounter.getId()));
        return diagnosticOrder;
    }

//    public DiagnosticOrder.Item createOrderItem(Order order, CodeableConceptDt orderCode) {
//        DiagnosticOrder.Item orderItem = new DiagnosticOrder.Item();
//        orderItem.setCode(orderCode);
//        if (isDiscontinuedOrder(order)) {
//            orderItem.setStatus(ProcedureRequest.ProcedureRequestStatus.CANCELLED);
//            addEvent(orderItem, ProcedureRequest.ProcedureRequestStatus.REQUESTED, order.getPreviousOrder().getDateActivated());
//            addEvent(orderItem, ProcedureRequest.ProcedureRequestStatus.CANCELLED, order.getDateActivated());
//        } else {
//            orderItem.setStatus(DiagnosticOrderStatusEnum.REQUESTED);
//            addEvent(orderItem, DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated());
//        }
//        return orderItem;
//    }

    private void setDiagnosticOrderId(SystemProperties systemProperties, ProcedureRequest diagnosticOrder, String orderUuid) {
        String id = new EntityReference().build(Order.class, systemProperties, orderUuid);
        diagnosticOrder.addIdentifier().setValue(id);
        diagnosticOrder.setId(id);
    }

    private boolean isDiscontinuedOrder(Order order) {
        return order.getAction().equals(Order.Action.DISCONTINUE);
    }

//    private void addEvent(DiagnosticOrder.Item orderItem, DiagnosticOrderStatusEnum status, Date dateActivated) {
//        DiagnosticOrder.Event event = orderItem.addEvent();
//        event.setStatus(status);
//        event.setDateTime(dateActivated, TemporalPrecisionEnum.MILLI);
//    }

    private Reference getOrdererReference(Order order, FHIREncounter fhirEncounter) {
        if (order.getOrderer() != null) {
            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
            if (providerUrl != null) {
                return new Reference().setReference(providerUrl);
            }
        }
        return fhirEncounter.getFirstParticipantReference();
    }
}
