package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.ProviderLookupService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.Order.Action.DISCONTINUE;
import static org.openmrs.module.fhir.MRSProperties.MRS_PROCEDURE_ORDER_TYPE;

@Component
public class ProcedureOrderMapper implements EmrOrderResourceHandler {
    private final static String PROCEDURE_REQUEST_RESOURCE_DISPLAY = "Procedure Request";
    @Autowired
    private ProviderLookupService providerLookupService;
    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private ProvenanceMapper provenanceMapper;

    @Override
    public boolean canHandle(Order order) {
        return order.getOrderType().getName().equalsIgnoreCase(MRS_PROCEDURE_ORDER_TYPE);
    }

    @Override
    public List<FHIRResource> map(Order order, FHIREncounter fhirEncounter, Bundle bundle, SystemProperties systemProperties) {
        if (order.getDateStopped() != null) return Collections.EMPTY_LIST;
        List<FHIRResource> resources = new ArrayList<>();
        FHIRResource procedureRequest = createProcedureRequest(order, fhirEncounter, systemProperties);
        if (null != procedureRequest) {
            resources.add(procedureRequest);
            resources.add(provenanceMapper.map(order, fhirEncounter, procedureRequest));
        }
        return resources;
    }

    public FHIRResource createProcedureRequest(Order order, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        ProcedureRequest procedureRequest = new ProcedureRequest();
        procedureRequest.setSubject(fhirEncounter.getPatient());
//        procedureRequest.setOrderer(getOrdererReference(order, fhirEncounter));
        procedureRequest.setAuthoredOn(order.getDateActivated());
        String id = new EntityReference().build(Order.class, systemProperties, order.getUuid());
        procedureRequest.addIdentifier().setValue(id);
        procedureRequest.setId(id);
        procedureRequest.setContext(new Reference().setReference(fhirEncounter.getId()));
        setOrderStatus(order, procedureRequest);
        Reference reference = procedureRequest.addRelevantHistory();
//        setPreviousOrder(order, procedureRequest, systemProperties);
        addNotes(order, procedureRequest);
        CodeableConcept code = findCodeForOrder(order);
        if (code == null) {
            return null;
        }
        procedureRequest.setCode(code);
        return new FHIRResource(PROCEDURE_REQUEST_RESOURCE_DISPLAY, procedureRequest.getIdentifier(), procedureRequest);
    }

    private void setOrderStatus(Order order, ProcedureRequest procedureRequest) {
        if (order.getAction().equals(DISCONTINUE))
            procedureRequest.setStatus(ProcedureRequest.ProcedureRequestStatus.SUSPENDED);
        else
            procedureRequest.setStatus(ProcedureRequest.ProcedureRequestStatus.ACTIVE);
    }

    private void addNotes(Order order, ProcedureRequest procedureRequest) {
        Annotation notes = new Annotation();
        notes.setText(order.getCommentToFulfiller());
        procedureRequest.addNote(notes);
    }

//    private ResourceReferenceDt getOrdererReference(Order order, FHIREncounter fhirEncounter) {
//        if (order.getOrderer() != null) {
//            String providerUrl = providerLookupService.getProviderRegistryUrl(order.getOrderer());
//            if (providerUrl != null) {
//                return new ResourceReferenceDt().setReference(providerUrl);
//            }
//        }
//        return fhirEncounter.getFirstParticipantReference();
//    }

    private CodeableConcept findCodeForOrder(Order order) {
        if (null == order.getConcept()) {
            return null;
        }
        return codeableConceptService.addTRCodingOrDisplay(order.getConcept());
    }

//    private void setPreviousOrder(Order order, ProcedureRequest procedureRequest, SystemProperties systemProperties) {
//        if (DISCONTINUE.equals(order.getAction())) {
//            String fhirExtensionUrl = getFhirExtensionUrl(PROCEDURE_REQUEST_PREVIOUS_REQUEST_EXTENSION_NAME);
//            String previousOrderUuid = order.getPreviousOrder().getUuid();
//            String previousOrderUrl = new EntityReference().build(Order.class, systemProperties, previousOrderUuid);
//            procedureRequest.addUndeclaredExtension(false, fhirExtensionUrl, new StringDt(previousOrderUrl));
//        }
//    }
}
