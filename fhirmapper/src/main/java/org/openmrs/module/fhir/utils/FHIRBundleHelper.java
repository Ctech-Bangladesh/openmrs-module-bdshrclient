package org.openmrs.module.fhir.utils;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;

public class FHIRBundleHelper {

    public static Composition getComposition(Bundle bundle) {
        Resource resource = identifyFirstResourceWithName(bundle, "Composition");
        return resource != null ? (Composition) resource : null;
    }

    public static Resource identifyFirstResourceWithName(Bundle bundle, String resourceName) {
        for (Bundle.BundleEntryComponent bundleEntry : bundle.getEntry()) {
            if (bundleEntry.getResource().getResourceType().name().equals(resourceName)) {
                return bundleEntry.getResource();
            }
        }
        return null;
    }

    public static List<Resource> identifyTopLevelResources(Bundle bundle) {
        List<Resource> compositionRefResources = getCompositionRefResources(bundle);
        HashSet<Reference> childRef = getChildReferences(compositionRefResources);

        List<Resource> topLevelResources = new ArrayList<>();

        for (Resource compositionRefResource : compositionRefResources) {
            if (!isChildReference(childRef, compositionRefResource.getId())) {
                topLevelResources.add(compositionRefResource);
            }
        }
        return topLevelResources;
    }

    public static List<Resource> identifyResourcesByName(Bundle bundle, String resourceName) {
        List<Resource> resources = new ArrayList<>();
        for (Bundle.BundleEntryComponent bundleEntry : bundle.getEntry()) {
            if (bundleEntry.getResource().getResourceType().name().equals(resourceName)) {
                resources.add(bundleEntry.getResource());
            }
        }
        return resources;
    }

    public static Encounter getEncounter(Bundle bundle) {
        Resource resource = findResourceByReference(bundle, getComposition(bundle).getEncounter());
        return resource != null ? (Encounter) resource : null;
    }

    public static Resource findResourceByReference(Bundle bundle, Reference reference) {
        List<Resource> matchedResources = findResourcesByReference(bundle, asList(reference));
        return matchedResources != null && !matchedResources.isEmpty() ? matchedResources.get(0) : null;
    }


    public static Resource findResourceByFirstReference(Bundle bundle, List<Reference> references) {
        List<Resource> matchedResources = findResourcesByReference(bundle, references);
        return matchedResources != null && !matchedResources.isEmpty() ? matchedResources.get(0) : null;
    }

    public static List<Resource> findResourcesByReference(Bundle bundle, List<Reference> references) {
        ArrayList<Resource> matchedResources = new ArrayList<>();

        for (Reference resourceRef : references) {
            IIdType resourceReference = resourceRef.getReferenceElement();
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                Resource entryResource = entry.getResource();
                IdType entryResourceId = entryResource.getIdElement();
                boolean hasFullUrlDefined = !org.apache.commons.lang3.StringUtils.isBlank(entry.getFullUrl());

                if (resourceReference.hasResourceType() && entryResourceId.hasResourceType()
                        && entryResourceId.getValue().equals(resourceReference.getValue())) {
                    matchedResources.add(entryResource);
                } else if (entryResourceId.getIdPart().equals(resourceReference.getIdPart())) {
                    matchedResources.add(entryResource);
                } else if (hasFullUrlDefined) {
                    if (entry.getFullUrl().endsWith(resourceReference.getIdPart())) {
                        matchedResources.add(entryResource);
                    }
                }
            }
        }

        return matchedResources.isEmpty() ? null : matchedResources;
    }

    private static boolean isChildReference(HashSet<Reference> childReferenceDts, String resourceRef) {
        for (Reference childRef : childReferenceDts) {
            if (!childRef.getReference().isEmpty() && childRef.getReference().equals(resourceRef)) {
                return true;
            }
        }
        return false;
    }

    private static List<Resource> getCompositionRefResources(Bundle bundle) {
        List<Resource> resources = new ArrayList<>();
        Composition composition = getComposition(bundle);
        for (Composition.SectionComponent section : composition.getSection()) {
            Resource resourceForReference = findResourceByFirstReference(bundle, section.getEntry());
            if (!(resourceForReference instanceof Encounter)) {
                resources.add(resourceForReference);
            }
        }
        return resources;
    }

    private static HashSet<Reference> getChildReferences(List<Resource> compositionRefResources) {
        List<Reference> childResourceReferences = new ArrayList<>();
        for (Resource compositionRefResource : compositionRefResources) {
            // todo: get all children for compositionRefResource
//            childResourceReferences.addAll(compositionRefResource.getAllPopulatedChildElementsOfType(Reference.class));
        }
        HashSet<Reference> childRef = new HashSet<>();
        childRef.addAll(childResourceReferences);
        return childRef;
    }
}
