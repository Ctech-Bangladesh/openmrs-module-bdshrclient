package org.openmrs.module.fhir.utils;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.BaseResource;
import org.hl7.fhir.dstu3.model.DiagnosticReport;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.EncounterIdMapping;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.openmrs.module.fhir.MRSProperties.MRS_CONCEPT_CLASS_LAB_SET;
import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;

@Component
public class FHIRDiagnosticReportRequestHelper {
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private IdMappingRepository idMappingRepository;
    @Autowired
    private OrderService orderService;

    public Order getOrder(DiagnosticReport diagnosticReport, Concept concept) {
        List<Reference> requestDetail = diagnosticReport.getBasedOn();
        Order order = findOrderFromOrderRequestDetail(concept, requestDetail);
        if (order != null) return order;
        order = findOrderFromEncounterRequestDetail(concept, requestDetail);
        return order;
    }

    private boolean isRunningOrder(Order order) {
        return Order.Action.NEW.equals(order.getAction()) && order.getDateStopped() == null;
    }

    private Order findOrderFromOrderRequestDetail(Concept concept, List<Reference> requestDetail) {
        for (Reference reference : requestDetail) {
            String requestDetailReference = reference.getReference();
            if (requestDetailReference.contains("#" + new ProcedureRequest().getResourceType().name())) {
                String orderId = new EntityReference().parse(BaseResource.class, requestDetailReference);
                String encounterId = new EntityReference().parse(Encounter.class, requestDetailReference);
                String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, encounterId, orderId);
                List<IdMapping> idMappingList = idMappingRepository.findMappingsByExternalId(externalId, IdMappingType.PROCEDURE_REQUEST);
                if (CollectionUtils.isNotEmpty(idMappingList)) {
                    for (IdMapping idMapping : idMappingList) {
                        Order order = orderService.getOrderByUuid(idMapping.getInternalId());
                        if (order.getConcept().equals(concept))
                            return order;
                    }
                }
            }
        }
        return null;
    }

    private Order findOrderFromEncounter(Set<Order> orders, Concept concept) {
        for (Order order : orders) {
            if (isRunningOrder(order)) {
                Concept orderConcept = order.getConcept();
                if (orderConcept.equals(concept)) {
                    return order;
                } else if (orderConcept.getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET)) {
                    for (Concept setMember : orderConcept.getSetMembers()) {
                        if (setMember.equals(concept)) {
                            return order;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Order findOrderFromEncounterRequestDetail(Concept concept, List<Reference> requestDetail) {
        for (Reference reference : requestDetail) {
            String requestDetailReference = reference.getReference();
            String encounterId = new EntityReference().parse(Encounter.class, requestDetailReference);
            if (encounterId != null) {
                EncounterIdMapping encounterIdMapping = (EncounterIdMapping) idMappingRepository.findByExternalId(encounterId, IdMappingType.ENCOUNTER);
                if (encounterIdMapping == null) {
                    throw new RuntimeException(String.format("Encounter with id [%s] is not yet synced.", encounterId));
                }
                Encounter orderEncounter = encounterService.getEncounterByUuid(encounterIdMapping.getInternalId());
                return findOrderFromEncounter(orderEncounter.getOrders(), concept);
            }
        }
        return null;
    }
}
