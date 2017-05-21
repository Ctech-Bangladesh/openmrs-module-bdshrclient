package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.*;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.OrderIdMapping;
import org.openmrs.module.shrclient.util.StringUtil;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.CANCELLED;
import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.NEW;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.utils.FHIREncounterUtil.getIdPart;

@Component
public class FHIRProcedureRequestToLabOrderMapper implements FHIRResourceMapper {

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

    @Autowired
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Override
    public boolean canHandle(Resource resource) {
        if (resource instanceof ProcedureRequest) {
            return hasLabCategory(((ProcedureRequest) resource).getCategory());
        }
        return false;
    }

    private boolean hasLabCategory(List<CodeableConcept> category) {
        //todo: check for system as well
        Coding codeableConcept = category.get(0).getCodingFirstRep();
        return codeableConcept.getCode().equals(TR_ORDER_TYPE_LAB_CODE);
    }


    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        ProcedureRequest procedureRequest = (ProcedureRequest) resource;
        if (isProcedureRequestDownloaded(shrEncounterBundle, procedureRequest)) return;
        Order order = createLabOrder(procedureRequest, emrEncounter, shrEncounterBundle, systemProperties);
        if (order != null) {
            emrEncounter.addOrder(order);
        }
    }

    private boolean isProcedureRequestDownloaded(ShrEncounterBundle shrEncounterBundle, ProcedureRequest procedureRequest) {
        //todo: check for existing data as well
        String id = getIdPart(procedureRequest.getId());
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), id);
        IdMapping mapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_ORDER);
        return null != mapping;
    }

    private Order createLabOrder(ProcedureRequest procedureRequest, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Order order = new Order();
        if (CANCELLED.equals(procedureRequest.getStatus())) {
            Order previousOrder = getPreviousOrder(procedureRequest, shrEncounterBundle);
            if (null == previousOrder) return null;
            order.setPreviousOrder(previousOrder);
        }
        String orderType = getOrderType(procedureRequest, systemProperties);
        if (orderType == null) return null;
        order.setOrderType(orderService.getOrderTypeByName(orderType));
        Concept concept = omrsConceptLookup.findConceptByCode(procedureRequest.getCode().getCoding());
        if (null == concept) return null;
        order.setConcept(concept);
        order.setAction(CANCELLED.equals(procedureRequest.getStatus()) ? DISCONTINUE : NEW);
        order.setCareSetting(orderCareSettingLookupService.getCareSetting());

        String reference = procedureRequest.getRequester().getAgent().getReference();
        order.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(reference));
        Date dateActivate = getDateActivate(procedureRequest, emrEncounter);
        order.setDateActivated(dateActivate);
        order.setAutoExpireDate(DateUtil.addMinutes(dateActivate, ORDER_AUTO_EXPIRE_DURATION_MINUTES));
        order.setCommentToFulfiller(procedureRequest.getNoteFirstRep().getText());
        addTestOrderToIdMapping(order, procedureRequest, shrEncounterBundle, systemProperties);
        return order;
    }

    private Order getPreviousOrder(ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle) {
        Reference provenanceRef = procedureRequest.getRelevantHistoryFirstRep();
        String previousRequestResourceId = getIdPart(getResourceId(provenanceRef));
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), previousRequestResourceId);
        IdMapping idMapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_ORDER);
        if (null != idMapping) {
            return orderService.getOrderByUuid(idMapping.getInternalId());
        }
        return null;
    }

    private String getResourceId(Reference provenanceRef) {
        return StringUtil.removeSuffix(provenanceRef.getReference(), "-provenance");
    }

    private Date getDateActivate(ProcedureRequest procedureRequest, EmrEncounter emrEncounter) {
        Date orderedOn = procedureRequest.getAuthoredOn();
        if (null != orderedOn) return orderedOn;
        Date encounterDatetime = emrEncounter.getEncounter().getEncounterDatetime();
        if (ProcedureRequest.ProcedureRequestStatus.CANCELLED.equals(procedureRequest.getStatus())) {
            return DateUtil.aSecondAfter(encounterDatetime);
        }
        return encounterDatetime;
    }

    private void addTestOrderToIdMapping(Order order, ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        String shrOrderId = getIdPart(procedureRequest.getId());
        String orderUrl = getOrderUrl(shrEncounterBundle, systemProperties, shrOrderId);
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(),
                shrOrderId);
        OrderIdMapping orderIdMapping = new OrderIdMapping(order.getUuid(), externalId, IdMappingType.PROCEDURE_ORDER, orderUrl);
        idMappingRepository.saveOrUpdateIdMapping(orderIdMapping);
    }

    private String getOrderUrl(ShrEncounterBundle encounterComposition, SystemProperties systemProperties, String shrOrderId) {
        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, encounterComposition.getHealthId());
        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, encounterComposition.getShrEncounterId());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceType().name());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrOrderId);
        return new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
    }

    private String getOrderType(ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        List<OpenMRSOrderTypeMap> orderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        CodeableConcept category = procedureRequest.getCategoryFirstRep();
        if (category.isEmpty()) {
            return MRS_LAB_ORDER_TYPE;
        }
        String trValuesetUrl = systemProperties.createValueSetUrlFor(MRSProperties.TR_ORDER_TYPE_VALUESET_NAME);
        Coding coding = category.getCodingFirstRep();
        if (trValuesetUrl.equals(coding.getSystem())) {
            if (MRSProperties.TR_ORDER_TYPE_LAB_CODE.equals(coding.getCode())) {
                return MRS_LAB_ORDER_TYPE;
            }
            for (OpenMRSOrderTypeMap orderType : orderTypes) {
                if (orderType.getCode().equals(coding.getCode())) return orderType.getType();
            }
        }
        return null;
    }


    private void createOrders(ShrEncounterBundle shrEncounterBundle, ProcedureRequest diagnosticOrder, EmrEncounter emrEncounter, SystemProperties systemProperties) {
//        List<IdMapping> idMappingList = fetchOrdersByExternalId(shrEncounterBundle.getShrEncounterId(), getIdPart(diagnosticOrder.getId()));
//        List<DiagnosticOrder.Item> cancelledItems = getCancelledDiagnosticOrderItems(diagnosticOrder);
//        for (DiagnosticOrder.Item item : cancelledItems) {
//            cancelOrderForItem(diagnosticOrder, item, emrEncounter, idMappingList);
//        }
//        List<DiagnosticOrder.Item> requestedItems = getRequestedDiagnosticOrderItems(diagnosticOrder);
//        for (DiagnosticOrder.Item item : requestedItems) {
//            createOrderForItem(diagnosticOrder, item, shrEncounterBundle, emrEncounter, idMappingList, systemProperties);
//        }
//        createOrderForItem(diagnosticOrder, shrEncounterBundle, emrEncounter, idMappingList, systemProperties);

    }

//    private List<DiagnosticOrder.Item> getRequestedDiagnosticOrderItems(DiagnosticOrder diagnosticOrder) {
//        ArrayList<DiagnosticOrder.Item> items = new ArrayList<>();
//        for (DiagnosticOrder.Item item : diagnosticOrder.getItem()) {
//            if (isRequestedOrder(item, diagnosticOrder))
//                items.add(item);
//        }
//        return items;
//    }

//    private List<DiagnosticOrder.Item> getCancelledDiagnosticOrderItems(DiagnosticOrder diagnosticOrder) {
//        ArrayList<DiagnosticOrder.Item> items = new ArrayList<>();
//        for (DiagnosticOrder.Item item : diagnosticOrder.getItem()) {
//            if (isCancelledOrder(item, diagnosticOrder))
//                items.add(item);
//        }
//        return items;
//    }

//    private void createOrderForItem(ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle, EmrEncounter emrEncounter, List<IdMapping> idMappingList, SystemProperties systemProperties) {
//        Concept orderConcept = omrsConceptLookup.findConceptByCode(procedureRequest.getCode().getCoding());
//        if (orderConcept != null) {
//            Order existingRunningOrder = getExistingRunningOrder(idMappingList, orderConcept, emrEncounter);
//            if (existingRunningOrder != null)
//                return;
//            Order order = createRequestedOrder(procedureRequest, emrEncounter, orderConcept);
//            if (order == null) return;
//            addOrderToIdMapping(order, procedureRequest, shrEncounterBundle, systemProperties);
//            emrEncounter.addOrder(order);
//        }
//    }
//
//    private void addOrderToIdMapping(Order order, ProcedureRequest diagnosticOrder, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
//        String shrOrderId = getIdPart(diagnosticOrder.getId());
//        String orderUrl = getOrderUrl(shrEncounterBundle, systemProperties, shrOrderId);
//        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), shrOrderId);
//        OrderIdMapping orderIdMapping = new OrderIdMapping(order.getUuid(), externalId, IdMappingType.DIAGNOSTIC_ORDER, orderUrl, new Date());
//        idMappingRepository.saveOrUpdateIdMapping(orderIdMapping);
//    }
//
//    private String getOrderUrl(ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties, String shrOrderId) {
//        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
//        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, shrEncounterBundle.getHealthId());
//        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, shrEncounterBundle.getShrEncounterId());
//        orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceType().name());
//        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrOrderId);
//        return new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
//    }
//
//    private List<IdMapping> fetchOrdersByExternalId(String shrEncounterId, String orderId) {
//        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterId, orderId);
//        return idMappingRepository.findMappingsByExternalId(externalId, IdMappingType.DIAGNOSTIC_ORDER);
//    }

//    private void cancelOrderForItem(DiagnosticOrder diagnosticOrder, DiagnosticOrder.Item item, EmrEncounter emrEncounter, List<IdMapping> idMappingList) {
//        Concept orderConcept = omrsConceptLookup.findConceptByCode(item.getCode().getCoding());
//        if (orderConcept != null) {
//            Order existingRunningOrder = getExistingRunningOrder(idMappingList, orderConcept, emrEncounter);
//            if (existingRunningOrder == null) return;
//            Order order = createCancelledOrder(item, diagnosticOrder, emrEncounter, orderConcept, existingRunningOrder);
//            emrEncounter.addOrder(order);
//        }
//    }

//    private boolean isExistingOrderDiscontinued(Order existingRunningOrder, EmrEncounter emrEncounter) {
//        for (Order order : emrEncounter.getOrders()) {
//            if (existingRunningOrder.equals(order.getPreviousOrder())) return true;
//        }
//        return false;
//    }

//    private Order createCancelledOrder(DiagnosticOrder.Item item, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter, Concept orderConcept, Order existingRunningOrder) {
//        Date dateActivated = getDateActivatedFromEventWithStatus(item, diagnosticOrder, ProcedureRequestStatus.SUSPENDED);
//        if (dateActivated == null)
//            dateActivated = DateUtil.aSecondAfter(emrEncounter.getEncounter().getEncounterDatetime());
//        Order order = createOrder(diagnosticOrder, orderConcept, dateActivated);
//        if (null == order) return null;
//        order.setAction(Order.Action.DISCONTINUE);
//        order.setOrderReasonNonCoded(ORDER_DISCONTINUE_REASON);
//        order.setPreviousOrder(existingRunningOrder);
//        return order;
//    }

//    private Order createRequestedOrder(ProcedureRequest diagnosticOrder, EmrEncounter emrEncounter, Concept orderConcept) {
//        Date dateActivated = getDateActivatedFromEventWithStatus(diagnosticOrder, ProcedureRequest.ProcedureRequestStatus.ACTIVE);
//        if (dateActivated == null) dateActivated = emrEncounter.getEncounter().getEncounterDatetime();
//        return createOrder(diagnosticOrder, orderConcept, dateActivated);
//    }
//
//    private Order createOrder(ProcedureRequest diagnosticOrder, Concept orderConcept, Date dateActivated) {
//        Order order = new Order();
//        String orderType = getOrderType(diagnosticOrder);
//        if (null == orderType) {
//            return null;
//        }
//        order.setOrderType(orderService.getOrderTypeByName(orderType));
//        order.setConcept(orderConcept);
//        setOrderer(order, diagnosticOrder);
//        order.setCareSetting(orderCareSettingLookupService.getCareSetting());
//        order.setDateActivated(dateActivated);
//        order.setAutoExpireDate(getAutoExpireDate(dateActivated));
//        return order;
//    }
//
//    private Date getDateActivatedFromEventWithStatus(ProcedureRequest diagnosticOrder, ProcedureRequest.ProcedureRequestStatus status) {
//        if (diagnosticOrder.getStatus().equals(status))
//            return diagnosticOrder.getAuthoredOn();
//        DiagnosticOrder.Event event = getEvent(item.getEvent(), status);
//        if (event != null) return event.getDateTime();
//        event = getEvent(diagnosticOrder.getEvent(), status);
//        if (event != null) return event.getDateTime();
//        return null;
//    }

//    private DiagnosticOrder.Event getEvent(List<DiagnosticOrder.Event> events, DiagnosticOrderStatusEnum requested) {
//        for (DiagnosticOrder.Event event : events) {
//            if (event.getStatus().equals(requested.getCode())) return event;
//        }
//        return null;
//    }

//    private Date getAutoExpireDate(Date encounterDatetime) {
//        return DateUtil.addMinutes(encounterDatetime, ORDER_AUTO_EXPIRE_DURATION_MINUTES);
//    }
//
//    private boolean isCancelledOrder(DiagnosticOrder.Item diagnosticOrderItemComponent, DiagnosticOrder diagnosticOrder) {
//        if (!isItemStatusEmpty(diagnosticOrderItemComponent.getStatus()))
//            return DiagnosticOrderStatusEnum.CANCELLED.getCode().equals(diagnosticOrderItemComponent.getStatus());
//        if (!isItemStatusEmpty(diagnosticOrder.getStatus()))
//            return DiagnosticOrderStatusEnum.CANCELLED.getCode().equals(diagnosticOrder.getStatus());
//        return false;
//    }
//
//    private boolean isRequestedOrder(DiagnosticOrder.Item diagnosticOrderItemComponent, DiagnosticOrder diagnosticOrder) {
//        if (!isItemStatusEmpty(diagnosticOrderItemComponent.getStatus()))
//            return DiagnosticOrderStatusEnum.REQUESTED.getCode().equals(diagnosticOrderItemComponent.getStatus());
//        if (!isItemStatusEmpty(diagnosticOrder.getStatus()))
//            return DiagnosticOrderStatusEnum.REQUESTED.getCode().equals(diagnosticOrder.getStatus());
//        return true;
//    }
//
//    private boolean isItemStatusEmpty(String status) {
//        return status == null || StringUtils.isEmpty(status);
//    }
//
//    private Order getExistingRunningOrder(List<IdMapping> orderIdMappings, Concept orderConcept, EmrEncounter emrEncounter) {
//        for (IdMapping orderIdMapping : orderIdMappings) {
//            Order order = orderService.getOrderByUuid(orderIdMapping.getInternalId());
//            if (order.getConcept().equals(orderConcept) && isRunningOrder(order, emrEncounter)) {
//                return order;
//            }
//        }
//        return null;
//    }
//
//    private boolean isRunningOrder(Order order, EmrEncounter emrEncounter) {
//        return Order.Action.NEW.equals(order.getAction()) && order.getDateStopped() == null
//                && !isExistingOrderDiscontinued(order, emrEncounter);
//    }
//
//    private void setOrderer(Order order, ProcedureRequest diagnosticOrder) {
//        Reference orderer = diagnosticOrder.getRequester().getAgent();
//        String practitionerReferenceUrl = null;
//        if (orderer != null && !orderer.isEmpty()) {
//            practitionerReferenceUrl = orderer.getReference();
//        }
//        order.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(practitionerReferenceUrl));
//    }
}
