package org.openmrs.module.fhir;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.openmrs.module.fhir.mapper.model.FHIRResource;

import java.util.ArrayList;
import java.util.List;

public class TestFhirFeedHelper {

    public static List<Resource> getResourceByType(Bundle bundle, String resourceType) {
        List<Resource> resources = new ArrayList<>();
        List<Bundle.BundleEntryComponent> entryList = bundle.getEntry();
        for (Bundle.BundleEntryComponent bundleEntry : entryList) {
            Resource resource = bundleEntry.getResource();
            if (resource.getResourceType().name().equals(resourceType)) {
                resources.add(resource);
            }
        }
        return resources;
    }

    public static FHIRResource getResourceByReference(Reference reference, List<FHIRResource> fhirResources) {
        for (FHIRResource fhirResource : fhirResources) {
            if(fhirResource.getIdentifier().getValue().equals(reference.getReference())) {
                return fhirResource;
            }
        }
        return null;
    }

    public static FHIRResource getFirstResourceByType(String fhirResourceName, List<FHIRResource> fhirResources) {
        for (FHIRResource fhirResource : fhirResources) {
            if(fhirResourceName.equals(fhirResource.getResource().getResourceType().name())) {
                return fhirResource;
            }
        }
        return null;
    }

    public static ArrayList<FHIRResource> getResourceByType(String resourceName, List<FHIRResource> fhirResources) {
        ArrayList<FHIRResource> mappedFhirResources = new ArrayList<>();
        for (FHIRResource fhirResource : fhirResources) {
            if(resourceName.equals(fhirResource.getResource().getResourceType().name())) {
                mappedFhirResources.add(fhirResource);
            }
        }
        return mappedFhirResources;
    }
}
