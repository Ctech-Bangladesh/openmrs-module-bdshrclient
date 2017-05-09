package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

@Component("fhirRadiologyOrderMapper")
public class GenericOrderMapper implements EmrOrderResourceHandler {

    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private DiagnosticOrderBuilder orderBuilder;
    @Autowired
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Override
    public boolean canHandle(Order order) {
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
            if (order.getOrderType().getName().equals(openMRSOrderTypeMap.getType()))
                return true;
        }
        return false;
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;
        ProcedureRequest diagnosticOrder = orderBuilder.createDiagnosticOrder(order, fhirEncounter, systemProperties);
//        addExtension(diagnosticOrder, order);
//        createOrderItemForTest(order, diagnosticOrder, order.getConcept());
//        if (CollectionUtils.isEmpty(diagnosticOrder.getItem())) {
//            return null;
//        }
        FHIRResource fhirOrderResource = new FHIRResource("Diagnostic Order", diagnosticOrder.getIdentifier(), diagnosticOrder);
        List<FHIRResource> fhirResources = new ArrayList<>();
        fhirResources.add(fhirOrderResource);
        return fhirResources;
    }

//    private void addExtension(DiagnosticOrder diagnosticOrder, Order order) {
//        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
//        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
//            if (order.getOrderType().getName().equals(openMRSOrderTypeMap.getType())) {
//                String fhirExtensionUrl = getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME);
//                diagnosticOrder.addUndeclaredExtension(false, fhirExtensionUrl, new StringDt(openMRSOrderTypeMap.getCode()));
//            }
//        }
//    }
//
//    private void createOrderItemForTest(Order order, DiagnosticOrder diagnosticOrder, Concept concept) {
//        CodeableConceptDt orderCode = codeableConceptService.addTRCodingOrDisplay(concept);
//        diagnosticOrder.addItem(orderBuilder.createOrderItem(order, orderCode));
//    }
}
