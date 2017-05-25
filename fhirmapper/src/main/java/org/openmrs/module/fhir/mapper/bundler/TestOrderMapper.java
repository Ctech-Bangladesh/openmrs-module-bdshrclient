package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.ACTIVE;
import static org.hl7.fhir.dstu3.model.ProcedureRequest.ProcedureRequestStatus.CANCELLED;
import static org.openmrs.module.fhir.MRSProperties.MRS_LAB_ORDER_TYPE;
import static org.openmrs.module.fhir.MRSProperties.TR_ORDER_TYPE_LAB_CODE;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component("fhirTestOrderMapper")
public class TestOrderMapper implements EmrOrderResourceHandler {
    private static final String TR_CONCEPT_URI_PART = "/tr/concepts/";

    private final CodeableConceptService codeableConceptService;
    private final ProcedureRequestBuilder procedureRequestBuilder;

    @Autowired
    public TestOrderMapper(CodeableConceptService codeableConceptService, ProcedureRequestBuilder procedureRequestBuilder) {
        this.codeableConceptService = codeableConceptService;
        this.procedureRequestBuilder = procedureRequestBuilder;
    }

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_LAB_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;
        List<FHIRResource> fhirResources = new ArrayList<>();

        CodeableConcept trCodingForOrder = codeableConceptService.addTRCoding(order.getConcept());
        if (!isPanelOrder(order)) {
            createProcedureRequestForLabOrder(order, fhirEncounter, systemProperties, fhirResources, trCodingForOrder);
            return fhirResources;
        }
        if (isTrConcept(trCodingForOrder)) {
            String orderUuid = formatProcedureRequestId(getOrderUUid(order), getConceptCoding(trCodingForOrder.getCoding()).getCode());
            createProcedureRequest(order, fhirEncounter, systemProperties, fhirResources, trCodingForOrder, orderUuid);
            return fhirResources;
        }
        createProcedureRequestsForLocalPanel(order, fhirEncounter, systemProperties, fhirResources);
        return fhirResources;
    }

    private boolean isTrConcept(CodeableConcept trCodingForOrder) {
        return trCodingForOrder != null && !trCodingForOrder.isEmpty();
    }

    private boolean isPanelOrder(Order order) {
        return order.getConcept().getConceptClass().getName().equals(MRSProperties.MRS_CONCEPT_CLASS_LAB_SET);
    }

    private boolean isDiscontinuedOrder(Order order) {
        return Order.Action.DISCONTINUE.equals(order.getAction());
    }

    private void createProcedureRequestForLabOrder(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties, List<FHIRResource> fhirResources, CodeableConcept codingForOrder) {
        String orderUuid = getOrderUUid(order);
        if (!isTrConcept(codingForOrder)) {
            codingForOrder = codeableConceptService.addTRCodingOrDisplay(order.getConcept());
            orderUuid = formatProcedureRequestId(orderUuid, "1");
        } else {
            Coding conceptCoding = getConceptCoding(codingForOrder.getCoding());
            if (null == conceptCoding) {
                String message = String.format("The concept with id %s is a TR Concept but doesn't have Concept coding", order.getConcept().getId());
                throw new RuntimeException(message);
            }
            orderUuid = formatProcedureRequestId(orderUuid, conceptCoding.getCode());
        }
        createProcedureRequest(order, fhirEncounter, systemProperties,
                fhirResources, codingForOrder, orderUuid);
    }

    private void createProcedureRequest(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties,
                                        List<FHIRResource> fhirResources, CodeableConcept coding, String orderUuid) {
        boolean isDiscontinuedOrder = isDiscontinuedOrder(order);
        ProcedureRequest procedureRequest = procedureRequestBuilder.createProcedureRequest(order, fhirEncounter, systemProperties, TR_ORDER_TYPE_LAB_CODE, orderUuid);
        procedureRequest.setCode(coding);
        procedureRequest.setStatus(isDiscontinuedOrder ? CANCELLED : ACTIVE);
        procedureRequest.setAuthoredOn(order.getDateActivated());

        String resourceName = "Procedure Request";
        FHIRResource fhirProcedureRequest = new FHIRResource(resourceName, procedureRequest.getIdentifier(), procedureRequest);
        fhirResources.add(fhirProcedureRequest);

        FHIRResource provenance = createProvenance(resourceName, order.getDateActivated(), procedureRequest.getRequester().getAgent(), fhirProcedureRequest.getResource().getId());
        fhirResources.add(provenance);
    }

    private Coding getConceptCoding(List<Coding> codings) {
        return codings.stream().filter(
                coding -> StringUtils.isNotBlank(coding.getSystem()) && coding.getSystem().contains(TR_CONCEPT_URI_PART)
        ).findFirst().orElse(null);
    }

    private void createProcedureRequestsForLocalPanel(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties, List<FHIRResource> fhirResources) {
        int count = 1;
        for (Concept testConcept : order.getConcept().getSetMembers()) {
            CodeableConcept trCoding = codeableConceptService.addTRCoding(testConcept);
            String orderUuid;
            if (isTrConcept(trCoding)) {
                orderUuid = formatProcedureRequestId(getOrderUUid(order), getConceptCoding(trCoding.getCoding()).getCode());
            } else {
                trCoding = codeableConceptService.addTRCodingOrDisplay(testConcept);
                orderUuid = formatProcedureRequestId(getOrderUUid(order), String.valueOf(count++));
            }
            createProcedureRequest(order, fhirEncounter, systemProperties, fhirResources, trCoding, orderUuid);
        }
    }

    private String formatProcedureRequestId(String orderUUid, String idSuffix) {
        return String.format("%s#%s", orderUUid, idSuffix);
    }

    private String getOrderUUid(Order order) {
        String orderUuid = order.getUuid();
        if (isDiscontinuedOrder(order)) {
            orderUuid = order.getPreviousOrder().getUuid();
        }
        return orderUuid;
    }

}
