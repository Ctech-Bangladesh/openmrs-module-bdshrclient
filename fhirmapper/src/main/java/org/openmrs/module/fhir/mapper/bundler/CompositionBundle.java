package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.base.resource.ResourceMetadataMap;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.CompositionStatusEnum;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.utils.CodableConceptService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.mapper.FHIRProperties.*;

@Component
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class CompositionBundle {

    public static final String FHIR_CONFIDENTIALITY_SYSTEM = "http://hl7.org/fhir/v3/Confidentiality";
    public static final String CONFIDENTIALITY_NORMAL = "N";
    @Autowired
    private EncounterMapper encounterMapper;

    @Autowired
    private List<EmrObsResourceHandler> obsResourceHandlers;

    @Autowired
    private List<EmrOrderResourceHandler> orderResourceHandlers;

    @Autowired
    private CodableConceptService codableConceptService;

    public Bundle create(org.openmrs.Encounter emrEncounter, SystemProperties systemProperties) {
        Bundle bundle = new Bundle();
        Encounter fhirEncounter = encounterMapper.map(emrEncounter, systemProperties);
        Composition composition = createComposition(emrEncounter.getEncounterDatetime(), fhirEncounter, systemProperties);
        bundle.setType(BundleTypeEnum.DOCUMENT);
        //TODO: bundle.setBase("urn:uuid:");
        bundle.setId(UUID.randomUUID().toString());
        ResourceMetadataMap metadataMap = new ResourceMetadataMap();
        metadataMap.put(ResourceMetadataKeyEnum.UPDATED, composition.getDate());
        bundle.setResourceMetadata(metadataMap);
        final FHIRResource encounterResource = new FHIRResource("Encounter", fhirEncounter.getIdentifier(), fhirEncounter);
        addResourceSectionToComposition(composition, encounterResource, systemProperties);
        addBundleEntry(bundle, new FHIRResource("Composition", asList(composition.getIdentifier()), composition));
        addBundleEntry(bundle, encounterResource);

        final Set<Obs> observations = emrEncounter.getObsAtTopLevel(false);
        for (Obs obs : observations) {
            for (EmrObsResourceHandler handler : obsResourceHandlers) {
                if (handler.canHandle(obs)) {
                    List<FHIRResource> mappedResources = handler.map(obs, fhirEncounter, systemProperties);
                    if (CollectionUtils.isNotEmpty(mappedResources)) {
                        addResourcesToBundle(mappedResources, composition, bundle, systemProperties);
                    }
                }
            }
        }

        Set<org.openmrs.Order> orders = emrEncounter.getOrders();
        for (org.openmrs.Order order : orders) {
            for (EmrOrderResourceHandler handler : orderResourceHandlers) {
                if (handler.canHandle(order)) {
                    List<FHIRResource> mappedResources = handler.map(order, fhirEncounter, bundle, systemProperties);
                    if (CollectionUtils.isNotEmpty(mappedResources)) {
                        addResourcesToBundle(mappedResources, composition, bundle, systemProperties);
                    }
                }
            }
        }

        return bundle;
    }

    private void addResourcesToBundle(List<FHIRResource> mappedResources, Composition composition, Bundle bundle, SystemProperties systemProperties) {
        for (FHIRResource mappedResource : mappedResources) {
            addResourceSectionToComposition(composition, mappedResource, systemProperties);
            addBundleEntry(bundle, mappedResource);
        }
    }

    //TODO: reference should be a relative URL
    private void addResourceSectionToComposition(Composition composition, FHIRResource resource, SystemProperties systemProperties) {
        String resourceId = new EntityReference().build(IResource.class, systemProperties, resource.getIdentifier().getValue());
        ResourceReferenceDt resourceReference = new ResourceReferenceDt();
        resourceReference.setReference(resourceId);
        resourceReference.setDisplay(resource.getResourceName());
        composition.addSection().setContent(resourceReference);
    }

    @SuppressWarnings("unchecked")
    private void addBundleEntry(Bundle bundle, FHIRResource resource) {
        Bundle.Entry resourceEntry = new Bundle.Entry();
        resourceEntry.setResource(resource.getResource());
        bundle.addEntry(resourceEntry);
    }

    private Composition createComposition(Date encounterDateTime, Encounter encounter, SystemProperties systemProperties) {
        Composition composition = new Composition().setDate(encounterDateTime, TemporalPrecisionEnum.MILLI);
        ResourceReferenceDt encounterReference = new ResourceReferenceDt(encounter);
        composition.setEncounter(encounterReference);
        composition.setStatus(CompositionStatusEnum.FINAL);
        // TODO : remove creating the identifier if necessary. We can use resource Id to identify resources now.
        composition.setIdentifier(new IdentifierDt().setValue(new EntityReference().build(Composition.class, systemProperties, UUID.randomUUID().toString())));
        composition.setSubject(encounter.getPatient());
        ResourceReferenceDt resourceReferenceAuthor = composition.addAuthor();
        resourceReferenceAuthor.setReference(encounter.getServiceProvider().getReference());
        composition.setConfidentiality(CONFIDENTIALITY_NORMAL);
        composition.setType(codableConceptService.getFHIRCodeableConcept(LOINC_CODE_DETAILS_NOTE, FHIR_DOC_TYPECODES_URL, LOINC_DETAILS_NOTE_DISPLAY));
        return composition;
    }
}
