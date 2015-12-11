package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticOrder;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterComposition;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.OrderCareSettingLookupService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof DiagnosticOrder;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterComposition encounterComposition, SystemProperties systemProperties) {
        DiagnosticOrder diagnosticOrder = (DiagnosticOrder) resource;
        createTestOrders(encounterComposition.getBundle(), diagnosticOrder, emrEncounter);
    }

    private void createTestOrders(Bundle bundle, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter) {
        List<DiagnosticOrder.Item> item = diagnosticOrder.getItem();
        for (DiagnosticOrder.Item diagnosticOrderItemComponent : item) {
            Concept testOrderConcept = omrsConceptLookup.findConceptByCode(diagnosticOrderItemComponent.getCode().getCoding());
            if (testOrderConcept != null) {
                Order testOrder = createTestOrder(bundle, diagnosticOrder, emrEncounter, testOrderConcept);
                emrEncounter.addOrder(testOrder);
            }
        }
    }

    private Order createTestOrder(Bundle bundle, DiagnosticOrder diagnosticOrder, EmrEncounter emrEncounter, Concept testOrderConcept) {
        Order testOrder = new Order();
        testOrder.setOrderType( orderService.getOrderTypeByName("Lab Order"));
        testOrder.setConcept(testOrderConcept);
        setOrderer(testOrder, diagnosticOrder);
        testOrder.setDateActivated(emrEncounter.getEncounter().getEncounterDatetime());
        testOrder.setCareSetting(orderCareSettingLookupService.getCareSetting(bundle));
        return testOrder;
    }

    private void setOrderer(Order testOrder, DiagnosticOrder diagnosticOrder) {
        ResourceReferenceDt orderer = diagnosticOrder.getOrderer();
        String practitionerReferenceUrl = null;
        if (orderer != null && !orderer.isEmpty()) {
            practitionerReferenceUrl = orderer.getReference().getValue();
        }
        testOrder.setOrderer(providerLookupService.getProviderByReferenceUrlOrDefault(practitionerReferenceUrl));
    }
}
