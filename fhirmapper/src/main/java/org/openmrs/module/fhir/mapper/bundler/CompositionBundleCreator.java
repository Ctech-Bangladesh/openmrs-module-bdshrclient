package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.base.resource.ResourceMetadataMap;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.CompositionStatusEnum;
import ca.uhn.fhir.model.primitive.InstantDt;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.HibernateLazyLoader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.openmrs.module.fhir.FHIRProperties.*;

@Component
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class CompositionBundleCreator {

    public static final String CONFIDENTIALITY_NORMAL = "N";
    private final EncounterMapper encounterMapper;
    private final List<EmrObsResourceHandler> obsResourceHandlers;
    private final List<EmrOrderResourceHandler> orderResourceHandlers;
    private final CodeableConceptService codeableConceptService;

    @Autowired
    public CompositionBundleCreator(EncounterMapper encounterMapper, List<EmrObsResourceHandler> obsResourceHandlers, List<EmrOrderResourceHandler> orderResourceHandlers, CodeableConceptService codeableConceptService) {
        this.encounterMapper = encounterMapper;
        this.obsResourceHandlers = obsResourceHandlers;
        this.orderResourceHandlers = orderResourceHandlers;
        this.codeableConceptService = codeableConceptService;
    }

    public Bundle create(org.openmrs.Encounter emrEncounter, String healthId, SystemProperties systemProperties) {
        HibernateLazyLoader hibernateLazyLoader = new HibernateLazyLoader();
        FHIREncounter fhirEncounter = encounterMapper.map(emrEncounter, healthId, systemProperties);
        Composition composition = createComposition(emrEncounter.getEncounterDatetime(), fhirEncounter, systemProperties);
        Bundle bundle = createBundle(composition);
        addBundleEntry(bundle, new FHIRResource("Composition", asList(composition.getIdentifier()), composition));
        FHIRResource encounterResource = new FHIRResource("Encounter", fhirEncounter.getIdentifier(), fhirEncounter.getEncounter());
        addResourceSectionToComposition(composition, encounterResource);
        addBundleEntry(bundle, encounterResource);

        mapObs(emrEncounter, systemProperties, fhirEncounter, composition, bundle);
        mapOrders(emrEncounter, systemProperties, hibernateLazyLoader, fhirEncounter, composition, bundle);

        return bundle;
    }

    public void mapOrders(Encounter emrEncounter, SystemProperties systemProperties, HibernateLazyLoader hibernateLazyLoader, FHIREncounter fhirEncounter, Composition composition, Bundle bundle) {
        Set<Order> orders = emrEncounter.getOrders();
        for (Order order : orders) {
            order = hibernateLazyLoader.load(order);
            for (EmrOrderResourceHandler handler : orderResourceHandlers) {
                if (!handler.canHandle(order)) continue;
                List<FHIRResource> mappedResources = handler.map(order, fhirEncounter, bundle, systemProperties);
                if (CollectionUtils.isEmpty(mappedResources)) continue;
                addResourcesToBundle(mappedResources, composition, bundle);
            }
        }
    }

    public void mapObs(Encounter emrEncounter, SystemProperties systemProperties, FHIREncounter fhirEncounter, Composition composition, Bundle bundle) {
        final Set<Obs> observations = emrEncounter.getObsAtTopLevel(false);
        for (Obs obs : observations) {
            for (EmrObsResourceHandler handler : obsResourceHandlers) {
                if (!handler.canHandle(obs)) continue;
                List<FHIRResource> mappedResources = handler.map(obs, fhirEncounter, systemProperties);
                if (CollectionUtils.isEmpty(mappedResources)) continue;
                addResourcesToBundle(mappedResources, composition, bundle);
            }
        }
    }

    public Bundle createBundle(Composition composition) {
        Bundle bundle = new Bundle();
        bundle.setType(BundleTypeEnum.COLLECTION);
        //TODO: bundle.setBase("urn:uuid:");
        bundle.setId(UUID.randomUUID().toString());
        ResourceMetadataMap metadataMap = new ResourceMetadataMap();
        metadataMap.put(ResourceMetadataKeyEnum.UPDATED, new InstantDt(composition.getDate(), TemporalPrecisionEnum.MILLI));
        bundle.setResourceMetadata(metadataMap);
        return bundle;
    }

    private void addResourcesToBundle(List<FHIRResource> mappedResources, Composition composition, Bundle bundle) {
        for (FHIRResource mappedResource : mappedResources) {
            addResourceSectionToComposition(composition, mappedResource);
            addBundleEntry(bundle, mappedResource);
        }
    }

    private void addResourceSectionToComposition(Composition composition, FHIRResource resource) {
        ResourceReferenceDt resourceReference = composition.addSection().addEntry();
        resourceReference.setReference(resource.getIdentifier().getValue());
        resourceReference.setDisplay(resource.getResourceName());
    }

    @SuppressWarnings("unchecked")
    private void addBundleEntry(Bundle bundle, FHIRResource resource) {
        Bundle.Entry resourceEntry = new Bundle.Entry();
        resourceEntry.setResource(resource.getResource());
        resourceEntry.setFullUrl(resource.getIdentifier().getValue());
        bundle.addEntry(resourceEntry);
    }

    private Composition createComposition(Date encounterDateTime, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        Composition composition = new Composition().setDate(encounterDateTime, TemporalPrecisionEnum.MILLI);
        composition.setEncounter(new ResourceReferenceDt().setReference(fhirEncounter.getId()));
        composition.setStatus(CompositionStatusEnum.FINAL);
        composition.setTitle("Patient Clinical Encounter");
        // TODO : remove creating the identifier if necessary. We can use resource Id to identify resources now.
        String id = new EntityReference().build(Composition.class, systemProperties, UUID.randomUUID().toString());
        composition.setId(id);
        composition.setIdentifier(new IdentifierDt().setValue(id));
        composition.setSubject(fhirEncounter.getPatient());
        composition.setAuthor(asList(fhirEncounter.getServiceProvider()));
        composition.setConfidentiality(CONFIDENTIALITY_NORMAL);
        composition.setType(codeableConceptService.getFHIRCodeableConcept(LOINC_CODE_DETAILS_NOTE, FHIR_DOC_TYPECODES_URL, LOINC_DETAILS_NOTE_DISPLAY));
        return composition;
    }
}
