package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.BaseResource;
import ca.uhn.fhir.model.dstu2.resource.ProcedureRequest;
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static ca.uhn.fhir.model.dstu2.valueset.ProcedureRequestStatusEnum.SUSPENDED;
import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.Order.Action.NEW;
import static org.openmrs.module.fhir.FHIRProperties.PROCEDURE_REQUEST_PREVIOUS_REQUEST_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.getFhirExtensionUrl;
import static org.openmrs.module.fhir.MRSProperties.ORDER_AUTO_EXPIRE_DURATION_MINUTES;
import static org.openmrs.module.fhir.MRSProperties.RESOURCE_MAPPING_EXTERNAL_ID_FORMAT;

@Component
public class FHIRProcedureRequestMapper implements FHIRResourceMapper {
    private final OrderService orderService;
    private final OMRSConceptLookup omrsConceptLookup;
    private final ProviderLookupService providerLookupService;
    private final OrderCareSettingLookupService orderCareSettingLookupService;
    private final IdMappingRepository idMappingRepository;

    @Autowired
    public FHIRProcedureRequestMapper(OrderService orderService, OMRSConceptLookup omrsConceptLookup, ProviderLookupService providerLookupService, OrderCareSettingLookupService orderCareSettingLookupService, IdMappingRepository idMappingRepository) {
        this.orderService = orderService;
        this.omrsConceptLookup = omrsConceptLookup;
        this.providerLookupService = providerLookupService;
        this.orderCareSettingLookupService = orderCareSettingLookupService;
        this.idMappingRepository = idMappingRepository;
    }

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof ProcedureRequest;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        ProcedureRequest procedureRequest = (ProcedureRequest) resource;
        if (isProcedureRequestDownloaded(shrEncounterBundle, procedureRequest)) return;
        Order order = createProcedureOrder(procedureRequest, emrEncounter, shrEncounterBundle, systemProperties);
        if (order != null) {
            emrEncounter.addOrder(order);
        }
    }

    private boolean isProcedureRequestDownloaded(ShrEncounterBundle shrEncounterBundle, ProcedureRequest procedureRequest) {
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), procedureRequest.getId().getIdPart());
        IdMapping mapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_ORDER);
        return null != mapping;
    }

    private Order createProcedureOrder(ProcedureRequest procedureRequest, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Order order = new Order();
        if (SUSPENDED.getCode().equals(procedureRequest.getStatus())) {
            Order previousOrder = getPreviousOrder(procedureRequest, shrEncounterBundle);
            if (null == previousOrder) return null;
            order.setPreviousOrder(previousOrder);
        }
            order.setOrderType(orderService.getOrderTypeByName(MRSProperties.MRS_PROCEDURE_ORDER_TYPE));
        Concept concept = omrsConceptLookup.findConceptByCode(procedureRequest.getCode().getCoding());
        if (null == concept) return null;
        order.setConcept(concept);
        setStatus(order, procedureRequest);
        order.setCareSetting(orderCareSettingLookupService.getCareSetting());
        setOrderer(order, procedureRequest);
        Date dateActivate = getDateActivate(procedureRequest, emrEncounter);
        order.setDateActivated(dateActivate);
        order.setAutoExpireDate(DateUtil.addMinutes(dateActivate, ORDER_AUTO_EXPIRE_DURATION_MINUTES));
        order.setCommentToFulfiller(procedureRequest.getNotesFirstRep().getText());
        addProcedureOrderToIdMapping(order, procedureRequest, shrEncounterBundle, systemProperties);
        return order;
    }

    private Order getPreviousOrder(ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle) {
        List<ExtensionDt> extensions = procedureRequest.getUndeclaredExtensionsByUrl(getFhirExtensionUrl(PROCEDURE_REQUEST_PREVIOUS_REQUEST_EXTENSION_NAME));
        if (extensions.isEmpty()) return null;
        String value = ((StringDt) extensions.get(0).getValue()).getValue();
        if (StringUtils.isBlank(value)) return null;
        String previousRequestResourceId = StringUtils.substringAfter(value, "urn:uuid:");
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), previousRequestResourceId);
        IdMapping idMapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_ORDER);
        if (null != idMapping) {
            return orderService.getOrderByUuid(idMapping.getInternalId());
        }
        return null;
    }

    private void addProcedureOrderToIdMapping(Order order, ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        String shrOrderId = procedureRequest.getId().getIdPart();
        String orderUrl = getOrderUrl(shrEncounterBundle, systemProperties, shrOrderId);
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), shrOrderId);
        OrderIdMapping orderIdMapping = new OrderIdMapping(order.getUuid(), externalId, IdMappingType.PROCEDURE_ORDER, orderUrl);
        idMappingRepository.saveOrUpdateIdMapping(orderIdMapping);
    }

    private String getOrderUrl(ShrEncounterBundle encounterComposition, SystemProperties systemProperties, String shrOrderId) {
        HashMap<String, String> orderUrlReferenceIds = new HashMap<>();
        orderUrlReferenceIds.put(EntityReference.HEALTH_ID_REFERENCE, encounterComposition.getHealthId());
        orderUrlReferenceIds.put(EntityReference.ENCOUNTER_ID_REFERENCE, encounterComposition.getShrEncounterId());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_RESOURCE_NAME, new ProcedureRequest().getResourceName());
        orderUrlReferenceIds.put(EntityReference.REFERENCE_ID, shrOrderId);
        return new EntityReference().build(BaseResource.class, systemProperties, orderUrlReferenceIds);
    }


    private void setStatus(Order order, ProcedureRequest procedureRequest) {
        if (SUSPENDED.getCode().equals(procedureRequest.getStatus())) {
            order.setAction(DISCONTINUE);
        } else {
            order.setAction(NEW);
        }
    }

    private Date getDateActivate(ProcedureRequest procedureRequest, EmrEncounter emrEncounter) {
        Date orderedOn = procedureRequest.getOrderedOn();
        if (null != orderedOn) return orderedOn;
        Date encounterDatetime = emrEncounter.getEncounter().getEncounterDatetime();
        if (SUSPENDED.getCode().equals(procedureRequest.getStatus())) {
            return DateUtil.aSecondAfter(encounterDatetime);
        }
        return encounterDatetime;
    }

    private void setOrderer(Order order, ProcedureRequest procedureRequest) {
        ResourceReferenceDt orderer = procedureRequest.getOrderer();
        String practitionerReferenceUrl = null;
        if (orderer != null && !orderer.isEmpty()) {
            practitionerReferenceUrl = orderer.getReference().getValue();
        }
        order.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(practitionerReferenceUrl));
    }

}
