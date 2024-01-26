package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Order;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.openmrs.module.fhir.utils.FHIRBundleHelper.PROVENANCE_ENTRY_URI_SUFFIX;

@Component
public class ProcedureRequestBuilder {
    private final ProviderLookupService providerLookupService;

    @Autowired
    public ProcedureRequestBuilder(ProviderLookupService providerLookupService) {
        this.providerLookupService = providerLookupService;
    }

    public ProcedureRequest createProcedureRequest(Order order, FHIREncounter fhirEncounter,
                                                   SystemProperties systemProperties, String orderTypeCode, String orderUuid) {
        ProcedureRequest procedureRequest = new ProcedureRequest();
        procedureRequest.setIntent(ProcedureRequest.ProcedureRequestIntent.ORDER);
        procedureRequest.setSubject(fhirEncounter.getPatient());
        procedureRequest.setRequester(new ProcedureRequest.ProcedureRequestRequesterComponent().setAgent(getOrdererReference(order, fhirEncounter)));
        setProcedureRequestId(systemProperties, procedureRequest, orderUuid);
        procedureRequest.setContext(new Reference().setReference(fhirEncounter.getId()));
        addCategory(procedureRequest, systemProperties, orderTypeCode);
        setHistory(order, procedureRequest, systemProperties);
        return procedureRequest;
    }

    private void addCategory(ProcedureRequest procedureRequest, SystemProperties systemProperties, String trOrderTypeCode) {
        Coding coding = procedureRequest.addCategory().addCoding();
        coding.setCode(trOrderTypeCode);
        String trValuesetUrl = systemProperties.createValueSetUrlFor(MRSProperties.TR_ORDER_TYPE_VALUESET_NAME);
        coding.setSystem(trValuesetUrl);
    }

    private void setProcedureRequestId(SystemProperties systemProperties, ProcedureRequest procedureRequest, String orderUuid) {
        String id = new EntityReference().build(Order.class, systemProperties, orderUuid);
        procedureRequest.addIdentifier().setValue(id);
        procedureRequest.setId(id);
    }

    private Reference getOrdererReference(Order order, FHIREncounter fhirEncounter) {
        if (order.getOrderer() != null) {
            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
            if (providerUrl != null) {
                return new Reference().setReference(providerUrl);
            }
        }
        return fhirEncounter.getFirstParticipantReference();
    }

    private void setHistory(Order order, ProcedureRequest procedureRequest, SystemProperties systemProperties) {
        Order previousOrder = order.getPreviousOrder();
        if (!order.getAction().equals(Order.Action.DISCONTINUE) || null == previousOrder) return;
        String previousOrderUuid = previousOrder.getUuid();
        String previousOrderUri = new EntityReference().build(Order.class, systemProperties, previousOrderUuid);
        Reference reference = procedureRequest.addRelevantHistory();
        reference.setReference(buildProvenanceReference(previousOrderUri));
    }

    private String buildProvenanceReference(String resourceEntryUri) {
        return resourceEntryUri + PROVENANCE_ENTRY_URI_SUFFIX;
    }

}
