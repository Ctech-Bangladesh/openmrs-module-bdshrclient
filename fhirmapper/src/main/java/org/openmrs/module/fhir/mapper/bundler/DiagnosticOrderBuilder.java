package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class DiagnosticOrderBuilder {
    @Autowired
    private ProviderLookupService providerLookupService;

    public DiagnosticOrder createDiagnosticOrder(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        DiagnosticOrder diagnosticOrder = new DiagnosticOrder();
        diagnosticOrder.setSubject(fhirEncounter.getPatient());
        diagnosticOrder.setOrderer(getOrdererReference(order, fhirEncounter));
        String orderUuid = order.getUuid();
        if (isDiscontinuedOrder(order))
            orderUuid = order.getPreviousOrder().getUuid();
        setDiagnosticOrderId(systemProperties, diagnosticOrder, orderUuid);
        diagnosticOrder.setEncounter(new ResourceReferenceDt().setReference(fhirEncounter.getId()));
        return diagnosticOrder;
    }

    public DiagnosticOrder.Item createOrderItem(Order order, CodeableConceptDt orderCode) {
        DiagnosticOrder.Item orderItem = new DiagnosticOrder.Item();
        orderItem.setCode(orderCode);
        if (isDiscontinuedOrder(order)) {
            orderItem.setStatus(DiagnosticOrderStatusEnum.CANCELLED);
            addEvent(orderItem, DiagnosticOrderStatusEnum.REQUESTED, order.getPreviousOrder().getDateActivated());
            addEvent(orderItem, DiagnosticOrderStatusEnum.CANCELLED, order.getDateActivated());
        } else {
            orderItem.setStatus(DiagnosticOrderStatusEnum.REQUESTED);
            addEvent(orderItem, DiagnosticOrderStatusEnum.REQUESTED, order.getDateActivated());
        }
        return orderItem;
    }

    private void setDiagnosticOrderId(SystemProperties systemProperties, DiagnosticOrder diagnosticOrder, String orderUuid) {
        String id = new EntityReference().build(Order.class, systemProperties, orderUuid);
        diagnosticOrder.addIdentifier().setValue(id);
        diagnosticOrder.setId(id);
    }

    private boolean isDiscontinuedOrder(Order order) {
        return order.getAction().equals(Order.Action.DISCONTINUE);
    }

    private void addEvent(DiagnosticOrder.Item orderItem, DiagnosticOrderStatusEnum status, Date dateActivated) {
        DiagnosticOrder.Event event = orderItem.addEvent();
        event.setStatus(status);
        event.setDateTime(dateActivated, TemporalPrecisionEnum.MILLI);
    }

    private ResourceReferenceDt getOrdererReference(Order order, FHIREncounter fhirEncounter) {
        if (order.getOrderer() != null) {
            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
            if (providerUrl != null) {
                return new ResourceReferenceDt().setReference(providerUrl);
            }
        }
        return fhirEncounter.getFirstParticipantReference();
    }
}
