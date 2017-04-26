package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.primitive.StringDt;
import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.FHIRProperties.DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME;
import static org.openmrs.module.fhir.FHIRProperties.getFhirExtensionUrl;

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
        DiagnosticRequest diagnosticOrder = orderBuilder.createDiagnosticOrder(order, fhirEncounter, systemProperties);
        addExtension(diagnosticOrder, order);
        createOrderItemForTest(order, diagnosticOrder, order.getConcept());
        if (CollectionUtils.isEmpty(diagnosticOrder.getItem())) {
            return null;
        }
        return asList(new FHIRResource("Diagnostic Order", diagnosticOrder.getIdentifier(), diagnosticOrder));
    }

    private void addExtension(DiagnosticOrder diagnosticOrder, Order order) {
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
            if (order.getOrderType().getName().equals(openMRSOrderTypeMap.getType())) {
                String fhirExtensionUrl = getFhirExtensionUrl(DIAGNOSTIC_ORDER_CATEGORY_EXTENSION_NAME);
                diagnosticOrder.addUndeclaredExtension(false, fhirExtensionUrl, new StringDt(openMRSOrderTypeMap.getCode()));
            }
        }
    }

    private void createOrderItemForTest(Order order, DiagnosticOrder diagnosticOrder, Concept concept) {
        CodeableConceptDt orderCode = codeableConceptService.addTRCodingOrDisplay(concept);
        diagnosticOrder.addItem(orderBuilder.createOrderItem(order, orderCode));
    }
}
