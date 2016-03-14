package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.BaseResource;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticOrder;
import ca.uhn.fhir.model.dstu2.valueset.DiagnosticOrderStatusEnum;
import ca.uhn.fhir.model.primitive.StringDt;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.OrderCareSettingLookupService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.OrderIdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class FHIRDiagnosticOrderMapper implements FHIRResourceMapper {

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private ProviderLookupService providerLookupService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCareSettingLookupService orderCareSettingLookupService;

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof DiagnosticOrder;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) resource;
        createOrders(shrEncounterBundle, diagnosticOrder, emrEncounter, systemProperties);
    }

    private void createOrders(ShrEncounterBundle shrEncounterBundle, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
        List<IdMapping> idMappingList = fetchOrdersByExternalId(shrEncounterBundle.getShrEncounterId(), diagnosticOrder.getId().getIdPart());
        List<DiagnosticOrder.Item> cancelledItems = getCancelledDiagnosticOrderItems(diagnosticOrder);
        for (DiagnosticOrder.Item item : cancelledItems) {
            cancelOrderForItem(diagnosticOrder, item, emrEncounter, idMappingList);
        }
        List<DiagnosticOrder.Item> requestedItems = getRequestedDiagnosticOrderItems(diagnosticOrder);
        for (DiagnosticOrder.Item item : requestedItems) {
            createOrderForItem(diagnosticOrder, item, shrEncounterBundle, emrEncounter, idMappingList, systemProperties);
        }
    }

    private List<DiagnosticOrder.Item> getRequestedDiagnosticOrderItems(DiagnosticOrder diagnosticOrder) {
        ArrayList<DiagnosticOrder.Item> items = new ArrayList<>();
        for (DiagnosticOrder.Item item : diagnosticOrder.getItem()) {
            if (isRequestedOrder(item, diagnosticOrder))
                items.add(item);
        }
        return items;
    }

    private List<DiagnosticOrder.Item> getCancelledDiagnosticOrderItems(DiagnosticOrder diagnosticOrder) {
        ArrayList<DiagnosticOrder.Item> items = new ArrayList<>();
        for (DiagnosticOrder.Item item : diagnosticOrder.getItem()) {
            if (isCancelledOrder(item, diagnosticOrder))
                items.add(item);
        }
        return items;
    }

    private void createOrderForItem(DiagnosticOrder diagnosticOrder, DiagnosticOrder.Item diagnosticOrderItemComponent, ShrEncounterBundle shrEncounterBundle, EmrEncounter emrEncounter, List<IdMapping> idMappingList, SystemProperties systemProperties) {
        Concept orderConcept = omrsConceptLookup.findConceptByCode(diagnosticOrderItemComponent.getCode().getCoding());
        if (orderConcept != null) {
            Order existingRunningOrder = getExistingRunningOrder(idMappingList, orderConcept, emrEncounter);
            if (existingRunningOrder != null)
                return;
            Order order = createRequestedOrder(diagnosticOrderItemComponent, diagnosticOrder, emrEncounter, orderConcept);
            addOrderToIdMapping(order, diagnosticOrder, shrEncounterBundle, systemProperties);
            emrEncounter.addOrder(order);
        }
    }

    private void addOrderToIdMapping(Order order, DiagnosticOrder diagnosticOrder, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        String shrOrderId = diagnosticOrder.getId().getIdPart();
        String orderUrl = getOrderUrl(shrEncounterBundle, systemProperties, shrOrderId);
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), shrOrderId);
        OrderIdMapping orderIdMapping = new OrderIdMapping(order.getUuid(), externalId, IdMappingType.DIAGNOSTIC_ORDER, orderUrl, new Date());
        idMappingRepository.saveOrUpdateIdMapping(orderIdMapping);
    }

    private String getOrderUrl(ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties, String shrOrderId) {
        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, shrEncounterBundle.getHealthId());
        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, shrEncounterBundle.getShrEncounterId());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new DiagnosticOrder().getResourceName());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrOrderId);
        return new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
    }

    private List<IdMapping> fetchOrdersByExternalId(String shrEncounterId, String orderId) {
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, orderId);
        return idMappingRepository.findMappingsByExternalId(externalId, IdMappingType.DIAGNOSTIC_ORDER);
    }

    private void cancelOrderForItem(DiagnosticOrder diagnosticOrder, DiagnosticOrder.Item item, EmrEncounter emrEncounter, List<IdMapping> idMappingList) {
        Concept orderConcept = omrsConceptLookup.findConceptByCode(item.getCode().getCoding());
        if (orderConcept != null) {
            Order existingRunningOrder = getExistingRunningOrder(idMappingList, orderConcept, emrEncounter);
            if (existingRunningOrder == null) return;
            Order order = createCancelledOrder(item, diagnosticOrder, emrEncounter, orderConcept, existingRunningOrder);
            emrEncounter.addOrder(order);
        }
    }

    private boolean isExistingOrderDiscontinued(Order existingRunningOrder, EmrEncounter emrEncounter) {
        for (Order order : emrEncounter.getOrders()) {
            if (existingRunningOrder.equals(order.getPreviousOrder())) return true;
        }
        return false;
    }

    private Order createCancelledOrder(DiagnosticOrder.Item item, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter, Concept orderConcept, Order existingRunningOrder) {
        Date dateActivated = getDateActivatedFromEventWithStatus(item, diagnosticOrder, DiagnosticOrderStatusEnum.CANCELLED);
        if (dateActivated == null)
            dateActivated = DateUtil.aSecondAfter(emrEncounter.getEncounter().getEncounterDatetime());
        Order order = createOrder(diagnosticOrder, orderConcept, dateActivated);
        order.setAction(Order.Action.DISCONTINUE);
        order.setOrderReasonNonCoded(MRSProperties.ORDER_DISCONTINUE_REASON);
        order.setPreviousOrder(existingRunningOrder);
        return order;
    }

    private Order createRequestedOrder(DiagnosticOrder.Item item, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter, Concept orderConcept) {
        Date dateActivated = getDateActivatedFromEventWithStatus(item, diagnosticOrder, DiagnosticOrderStatusEnum.REQUESTED);
        if (dateActivated == null) dateActivated = emrEncounter.getEncounter().getEncounterDatetime();
        return createOrder(diagnosticOrder, orderConcept, dateActivated);
    }

    private Order createOrder(DiagnosticOrder diagnosticOrder, Concept orderConcept, Date dateActivated) {
        Order order = new Order();
        String orderType = getOrderType(diagnosticOrder);
        order.setOrderType(orderService.getOrderTypeByName(orderType));
        order.setConcept(orderConcept);
        setOrderer(order, diagnosticOrder);
        order.setCareSetting(orderCareSettingLookupService.getCareSetting());
        order.setDateActivated(dateActivated);
        order.setAutoExpireDate(getAutoExpireDate(dateActivated));
        return order;
    }

    private String getOrderType(DiagnosticOrder diagnosticOrder) {
        List<ExtensionDt> undeclaredExtensionsByUrl = diagnosticOrder.getUndeclaredExtensionsByUrl(getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME));
        for (ExtensionDt extensionDt : undeclaredExtensionsByUrl) {
            if (((StringDt) extensionDt.getValue()).getValue().equals(FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE)) {
                return MRS_RADIOLOGY_ORDER_TYPE;
            }
        }
        return MRS_LAB_ORDER_TYPE;
    }

    private Date getDateActivatedFromEventWithStatus(DiagnosticOrder.Item item, DiagnosticOrder diagnosticOrder, DiagnosticOrderStatusEnum status) {
        DiagnosticOrder.Event event = getEvent(item.getEvent(), status);
        if (event != null) return event.getDateTime();
        event = getEvent(diagnosticOrder.getEvent(), status);
        if (event != null) return event.getDateTime();
        return null;
    }

    private DiagnosticOrder.Event getEvent(List<DiagnosticOrder.Event> events, DiagnosticOrderStatusEnum requested) {
        for (DiagnosticOrder.Event event : events) {
            if (event.getStatus().equals(requested.getCode())) return event;
        }
        return null;
    }

    private Date getAutoExpireDate(Date encounterDatetime) {
        return DateUtil.addMinutes(encounterDatetime, MRSProperties.ORDER_AUTO_EXPIRE_DURATION_MINUTES);
    }

    private boolean isCancelledOrder(DiagnosticOrder.Item diagnosticOrderItemComponent, DiagnosticOrder diagnosticOrder) {
        if (!isItemStatusEmpty(diagnosticOrderItemComponent.getStatus()))
            return DiagnosticOrderStatusEnum.CANCELLED.getCode().equals(diagnosticOrderItemComponent.getStatus());
        if (!isItemStatusEmpty(diagnosticOrder.getStatus()))
            return DiagnosticOrderStatusEnum.CANCELLED.getCode().equals(diagnosticOrder.getStatus());
        return false;
    }

    private boolean isRequestedOrder(DiagnosticOrder.Item diagnosticOrderItemComponent, DiagnosticOrder diagnosticOrder) {
        if (!isItemStatusEmpty(diagnosticOrderItemComponent.getStatus()))
            return DiagnosticOrderStatusEnum.REQUESTED.getCode().equals(diagnosticOrderItemComponent.getStatus());
        if (!isItemStatusEmpty(diagnosticOrder.getStatus()))
            return DiagnosticOrderStatusEnum.REQUESTED.getCode().equals(diagnosticOrder.getStatus());
        return true;
    }

    private boolean isItemStatusEmpty(String status) {
        return status == null || StringUtils.isEmpty(status);
    }

    private Order getExistingRunningOrder(List<IdMapping> orderIdMappings, Concept orderConcept, EmrEncounter emrEncounter) {
        for (IdMapping orderIdMapping : orderIdMappings) {
            Order order = orderService.getOrderByUuid(orderIdMapping.getInternalId());
            if (order.getConcept().equals(orderConcept) && isRunningOrder(order, emrEncounter)) {
                return order;
            }
        }
        return null;
    }

    private boolean isRunningOrder(Order order, EmrEncounter emrEncounter) {
        return Order.Action.NEW.equals(order.getAction()) && order.getDateStopped() == null
                && !isExistingOrderDiscontinued(order, emrEncounter);
    }

    private void setOrderer(Order order, DiagnosticOrder diagnosticOrder) {
        ResourceReferenceDt orderer = diagnosticOrder.getOrderer();
        String practitionerReferenceUrl = null;
        if (orderer != null && !orderer.isEmpty()) {
            practitionerReferenceUrl = orderer.getReference().getValue();
        }
        order.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(practitionerReferenceUrl));
    }
}
