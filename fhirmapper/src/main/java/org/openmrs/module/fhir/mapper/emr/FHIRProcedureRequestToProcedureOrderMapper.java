package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
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
import org.openmrs.module.shrclient.util.StringUtil;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.utils.FHIREncounterUtil.getIdPart;

@Component
public class FHIRProcedureRequestToProcedureOrderMapper implements FHIRResourceMapper {
    private final OrderService orderService;
    private final OMRSConceptLookup omrsConceptLookup;
    private final ProviderLookupService providerLookupService;
    private final OrderCareSettingLookupService orderCareSettingLookupService;
    private final IdMappingRepository idMappingRepository;

    @Autowired
    public FHIRProcedureRequestToProcedureOrderMapper(OrderService orderService, OMRSConceptLookup omrsConceptLookup, ProviderLookupService providerLookupService, OrderCareSettingLookupService orderCareSettingLookupService, IdMappingRepository idMappingRepository) {
        this.orderService = orderService;
        this.omrsConceptLookup = omrsConceptLookup;
        this.providerLookupService = providerLookupService;
        this.orderCareSettingLookupService = orderCareSettingLookupService;
        this.idMappingRepository = idMappingRepository;
    }

    @Override
    public boolean canHandle(Resource resource) {
        if (resource instanceof ProcedureRequest) {
            return hasProcedureCategory(((ProcedureRequest) resource).getCategory());
        }
        return false;
    }

    private boolean hasProcedureCategory(List<CodeableConcept> category) {
        //todo: check for system as well
        Coding codeableConcept = category.get(0).getCodingFirstRep();
        return codeableConcept.getCode().equals(TR_ORDER_TYPE_PROCEDURE_CODE);
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        ProcedureRequest procedureRequest = (ProcedureRequest) resource;
        if (isProcedureRequestDownloaded(shrEncounterBundle, procedureRequest)) return;
        Order order = createProcedureOrder(procedureRequest, emrEncounter, shrEncounterBundle, systemProperties);
        if (order != null) {
            emrEncounter.addOrder(order);
        }
    }


    private boolean isProcedureRequestDownloaded(ShrEncounterBundle shrEncounterBundle, ProcedureRequest procedureRequest) {
        String id = getIdPart(procedureRequest.getId());
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), id);
        IdMapping mapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_REQUEST);
        return null != mapping;
    }

    private Order createProcedureOrder(ProcedureRequest procedureRequest, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Order order = new Order();
        if (ProcedureRequest.ProcedureRequestStatus.CANCELLED.equals(procedureRequest.getStatus())) {
            Order previousOrder = getPreviousOrder(procedureRequest, shrEncounterBundle);
            if (null == previousOrder) return null;
            order.setPreviousOrder(previousOrder);
        }
        order.setOrderType(orderService.getOrderTypeByName(MRS_PROCEDURE_ORDER_TYPE));
        Concept concept = omrsConceptLookup.findConceptByCode(procedureRequest.getCode().getCoding());
        if (null == concept) return null;
        order.setConcept(concept);
        setStatus(order, procedureRequest);
        order.setCareSetting(orderCareSettingLookupService.getCareSetting());

        String reference = procedureRequest.getRequester().getAgent().getReference();
        if (StringUtils.isNotBlank(reference)) {
            order.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(reference));
        }
        Date dateActivate = getDateActivate(procedureRequest, emrEncounter);
        order.setDateActivated(dateActivate);
        order.setAutoExpireDate(DateUtil.addMinutes(dateActivate, ORDER_AUTO_EXPIRE_DURATION_MINUTES));
        order.setCommentToFulfiller(procedureRequest.getNoteFirstRep().getText());
        addProcedureOrderToIdMapping(order, procedureRequest, shrEncounterBundle, systemProperties);
        return order;
    }

    private Order getPreviousOrder(ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle) {
        Reference provenanceRef = procedureRequest.getRelevantHistoryFirstRep();
        String previousRequestResourceId = getIdPart(getResourceId(provenanceRef));
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(), previousRequestResourceId);
        IdMapping idMapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_REQUEST);
        if (null != idMapping) {
            return orderService.getOrderByUuid(idMapping.getInternalId());
        }
        return null;
    }

    private String getResourceId(Reference provenanceRef) {
        return StringUtil.removeSuffix(provenanceRef.getReference(), "-provenance");
    }

    private void addProcedureOrderToIdMapping(Order order, ProcedureRequest procedureRequest, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        String shrOrderId = getIdPart(procedureRequest.getId());
        String orderUrl = getOrderUrl(shrEncounterBundle, systemProperties, shrOrderId);
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, shrEncounterBundle.getShrEncounterId(),
                shrOrderId);
        OrderIdMapping orderIdMapping = new OrderIdMapping(order.getUuid(), externalId, IdMappingType.PROCEDURE_REQUEST, orderUrl);
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


    private void setStatus(Order order, ProcedureRequest procedureRequest) {
        if (ProcedureRequest.ProcedureRequestStatus.CANCELLED.equals(procedureRequest.getStatus())) {
            order.setAction(Order.Action.DISCONTINUE);
        } else {
            order.setAction(Order.Action.NEW);
        }
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
}
