package org.openmrs.module.fhir.mapper.bundler;


import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Provenance;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;

@Component("ProvenanceMapper")
public class ProvenanceMapper {

    @Autowired
    private ProviderLookupService providerLookupService;

    public FHIRResource map(Order order, FHIREncounter fhirEncounter, FHIRResource procedureRequest) {

        Provenance provenance = new Provenance();
        provenance.addAgent().setWho(getOrdererReference(order, fhirEncounter));
        provenance.setRecorded(order.getDateActivated());
        Reference reference = new Reference().setReference(procedureRequest.getResource().getId());
        provenance.setTarget(asList(reference));
        FHIRResource fhirResource = new FHIRResource("",new ArrayList<>(),provenance);
        return fhirResource;
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

}
