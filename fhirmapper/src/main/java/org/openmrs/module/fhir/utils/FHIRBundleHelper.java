package org.openmrs.module.fhir.utils;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IIdType;
import org.openmrs.module.fhir.mapper.model.FHIRResource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;

public class FHIRBundleHelper {
    public static final String PROVENANCE_ENTRY_URI_SUFFIX = "-provenance";

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
            // add all observation as part of observation target
            // add all observation as part of diagnosticreport result
            // add all diagnostic reports as part of procedure.report
            // add all medication requests as part of medicationrequest.priorprescription
            if (compositionRefResource instanceof DiagnosticReport) {
                DiagnosticReport diagnosticReport = (DiagnosticReport) compositionRefResource;
                childResourceReferences.addAll(diagnosticReport.getResult());
            }
            if (compositionRefResource instanceof MedicationRequest) {
                MedicationRequest medicationRequest = (MedicationRequest) compositionRefResource;
                Reference priorPrescription = medicationRequest.getPriorPrescription();
                if (!priorPrescription.isEmpty()) {
                    childResourceReferences.add(priorPrescription);
                }
            }
            if (compositionRefResource instanceof Procedure) {
                Procedure procedure = (Procedure) compositionRefResource;
                childResourceReferences.addAll(procedure.getReport());
            }
            if (compositionRefResource instanceof Observation) {
                List<Observation.ObservationRelatedComponent> related = ((Observation) compositionRefResource).getRelated();
                for (Observation.ObservationRelatedComponent observationRelatedComponent : related) {
                    childResourceReferences.add(observationRelatedComponent.getTarget());
                }
            }
        }
        HashSet<Reference> childRef = new HashSet<>();
        childRef.addAll(childResourceReferences);
        return childRef;
    }

    public static FHIRResource createProvenance(String resourceName, Date recorded, Reference agentReference, String targetReference) {
        Provenance provenance = new Provenance();
        provenance.setId(targetReference + PROVENANCE_ENTRY_URI_SUFFIX);
        provenance.addAgent().setWho(agentReference);
        provenance.setRecorded(recorded);
        Reference reference = new Reference().setReference(targetReference);
        provenance.addTarget(reference);
        return new FHIRResource(resourceName + " Provenance", asList(new Identifier().setValue(provenance.getId())), provenance);
    }

    public static Provenance getProvenanceForResource(Bundle bundle, String resourceId) {
        String provenanceId = resourceId + PROVENANCE_ENTRY_URI_SUFFIX;
        Bundle.BundleEntryComponent component = bundle.getEntry().stream().filter(
                entryComponent -> provenanceId.equals(entryComponent.getFullUrl())
        ).findFirst().orElse(null);
        return null == component ? null : (Provenance) component.getResource();
    }
}
