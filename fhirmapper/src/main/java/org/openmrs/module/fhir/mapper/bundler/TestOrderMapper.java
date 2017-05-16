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

    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private DiagnosticOrderBuilder orderBuilder;

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_LAB_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;
        List<FHIRResource> fhirResources = new ArrayList<>();

        CodeableConcept trCodingForOrder = codeableConceptService.addTRCoding(order.getConcept());
        if (order.getConcept().getConceptClass().getName().equals(MRSProperties.MRS_CONCEPT_CLASS_LAB_SET)) {
            if (trCodingForOrder != null && !trCodingForOrder.isEmpty()) {
                String orderUuid = order.getUuid();
                if (Order.Action.DISCONTINUE.equals(order.getAction())) {
                    orderUuid = order.getPreviousOrder().getUuid();
                }
                createProcedureRequest(order, fhirEncounter, systemProperties, fhirResources, trCodingForOrder, orderUuid);
            } else {
                int count = 1;
                for (Concept testConcept : order.getConcept().getSetMembers()) {
                    CodeableConcept trCoding = codeableConceptService.addTRCoding(testConcept);
                    if (trCoding != null && !trCoding.isEmpty()) {
                        String orderUuid = order.getUuid();
                        if (Order.Action.DISCONTINUE.equals(order.getAction())) {
                            orderUuid = order.getPreviousOrder().getUuid();
                        }
                        orderUuid = String.format("%s#%s", orderUuid, getConceptCoding(trCoding.getCoding()).getCode());
                        createProcedureRequest(order, fhirEncounter, systemProperties, fhirResources, trCoding, orderUuid);
                    } else {
                        trCoding = codeableConceptService.addTRCodingOrDisplay(testConcept);
                        String orderUuid = order.getUuid();
                        if (Order.Action.DISCONTINUE.equals(order.getAction())) {
                            orderUuid = order.getPreviousOrder().getUuid();
                        }
                        orderUuid = String.format("%s#%s", orderUuid, count++);
                        createProcedureRequest(order, fhirEncounter, systemProperties, fhirResources, trCoding, orderUuid);
                    }
                }
            }
        } else {
            createProcedureRequestForOrder(order, fhirEncounter, systemProperties, fhirResources, trCodingForOrder);
        }

//        if (CollectionUtils.isEmpty(procedureRequest.getItem())) {
//            return null;
//        }
//        procedureRequest.setCode();
        //        procedureRequest.setSpecimen();
//        procedureRequest.setStatus();


        return fhirResources;
    }

    private void createProcedureRequestForOrder(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties, List<FHIRResource> fhirResources, CodeableConcept codingForOrder) {
        if (codingForOrder == null || codingForOrder.isEmpty()) {
            codingForOrder = codeableConceptService.addTRCodingOrDisplay(order.getConcept());
        }
        String orderUuid = order.getUuid();
        if (Order.Action.DISCONTINUE.equals(order.getAction())) {
            orderUuid = order.getPreviousOrder().getUuid();
        }
        createProcedureRequest(order, fhirEncounter, systemProperties,
                fhirResources, codingForOrder, orderUuid);
    }

    private void createProcedureRequest(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties,
                                        List<FHIRResource> fhirResources, CodeableConcept coding, String orderUuid) {
        boolean isDiscontinuedOrder = Order.Action.DISCONTINUE.equals(order.getAction());
        ProcedureRequest procedureRequest = orderBuilder.createProcedureRequest(order, fhirEncounter, systemProperties, TR_ORDER_TYPE_LAB_CODE, orderUuid);
        procedureRequest.setCode(coding);
        procedureRequest.setStatus(isDiscontinuedOrder ? CANCELLED : ACTIVE);
        procedureRequest.setAuthoredOn(order.getDateActivated());

        if (isDiscontinuedOrder) {
            setHistory(order, procedureRequest, systemProperties);
        }

        FHIRResource fhirProcedureRequest = new FHIRResource("Procedure Request", procedureRequest.getIdentifier(), procedureRequest);
        fhirResources.add(fhirProcedureRequest);

        FHIRResource provenance = createProvenance(order.getDateActivated(), procedureRequest.getRequester().getAgent(), fhirProcedureRequest.getResource().getId());
        fhirResources.add(provenance);
    }

    private void setHistory(Order order, ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        Order previousOrder = order.getPreviousOrder();
        if (null == previousOrder) return;
        String previousOrderUuid = previousOrder.getUuid();
        String previousOrderUri = new EntityReference().build(Order.class, systemProperties, previousOrderUuid);
        Reference reference = procedureRequest.addRelevantHistory();
        reference.setReference(buildProvenanceReference(previousOrderUri));
    }

    private String buildProvenanceReference(String resourceEntryUri) {
        return resourceEntryUri + "-provenance";
    }


//    private void addItemsToDiagnosticOrder(Order order, DiagnosticOrder diagnosticOrder) {
//        if (order.getConcept().getConceptClass().getName().equals(MRSProperties.MRS_CONCEPT_CLASS_LAB_SET)) {
//            CodeableConceptDt panelOrderCode = codeableConceptService.addTRCoding(order.getConcept());
//            if (panelOrderCode != null && !panelOrderCode.isEmpty()) {
//                diagnosticOrder.addItem(orderBuilder.createOrderItem(order, panelOrderCode));
//            } else {
//                for (Concept testConcept : order.getConcept().getSetMembers()) {
//                    createOrderItemForTest(order, diagnosticOrder, testConcept);
//                }
//            }
//        } else {
//            createOrderItemForTest(order, diagnosticOrder, order.getConcept());
//        }
//    }

    private void createOrderItemForTest(Order order, ProcedureRequest diagnosticOrder, Concept concept) {

//        CodeableConcept orderCode = codeableConceptService.addTRCodingOrDisplay(concept);
//        diagnosticOrder.addItem(orderBuilder.createOrderItem(order, orderCode));
    }

    public static Coding getConceptCoding(List<Coding> codings) {
        return codings.stream().filter(
                coding -> StringUtils.isNotBlank(coding.getSystem()) && coding.getSystem().contains(TR_CONCEPT_URI_PART)
        ).findFirst().orElse(null);
    }
}