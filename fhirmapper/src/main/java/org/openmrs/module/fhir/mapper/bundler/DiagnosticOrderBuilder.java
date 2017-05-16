package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Order;
import org.openmrs.module.fhir.MRSProperties;
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

    public ProcedureRequest createProcedureRequest(Order order, FHIREncounter fhirEncounter,
                                                   SystemProperties systemProperties, String orderTypeCode, String orderUuid) {
        ProcedureRequest procedureRequest = new ProcedureRequest();
        procedureRequest.setIntent(ProcedureRequest.ProcedureRequestIntent.ORDER);
        procedureRequest.setSubject(fhirEncounter.getPatient());
        procedureRequest.setRequester(new ProcedureRequest.ProcedureRequestRequesterComponent().setAgent(getOrdererReference(order, fhirEncounter)));
        setProcedureRequesetId(systemProperties, procedureRequest, orderUuid);
        procedureRequest.setContext(new Reference().setReference(fhirEncounter.getId()));
        addCategory(procedureRequest, systemProperties, orderTypeCode);
        return procedureRequest;
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

    private void addCategory(ProcedureRequest procedureRequest, SystemProperties systemProperties, String trOrderTypeCode) {
        Coding coding = procedureRequest.addCategory().addCoding();
        coding.setCode(trOrderTypeCode);
        String trValuesetUrl = systemProperties.createValueSetUrlFor(MRSProperties.TR_ORDER_TYPE_VALUESET_NAME);
        coding.setSystem(trValuesetUrl);
    }

    private void setProcedureRequesetId(SystemProperties systemProperties, ProcedureRequest diagnosticOrder, String orderUuid) {
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
