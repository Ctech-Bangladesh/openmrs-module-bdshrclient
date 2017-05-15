package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MRSProperties.MRS_LAB_ORDER_TYPE;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.createProvenance;

@Component("fhirTestOrderMapper")
public class TestOrderMapper implements EmrOrderResourceHandler {

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
        ProcedureRequest diagnosticOrder = orderBuilder.createDiagnosticOrder(order, fhirEncounter, systemProperties);
        String fhirExtensionUrl = getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME);
        diagnosticOrder.addExtension(new Extension(fhirExtensionUrl, new StringType(FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE)));
//        addItemsToDiagnosticOrder(order, diagnosticOrder);
//        if (CollectionUtils.isEmpty(diagnosticOrder.getItem())) {
//            return null;
//        }
        List<FHIRResource> fhirResources = new ArrayList<>();
        FHIRResource fhirDiagnosticOrder = new FHIRResource("Diagnostic Order", diagnosticOrder.getIdentifier(), diagnosticOrder);
        fhirResources.add(fhirDiagnosticOrder);
        FHIRResource provenance = createProvenance(order.getDateActivated(), new Reference(), fhirDiagnosticOrder.getResource().getId());
        fhirResources.add(provenance);
        return fhirResources;
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
//
    private void createOrderItemForTest(Order order, ProcedureRequest diagnosticOrder, Concept concept) {
        CodeableConcept orderCode = codeableConceptService.addTRCodingOrDisplay(concept);
//        diagnosticOrder.addItem(orderBuilder.createOrderItem(order, orderCode));
    }
}