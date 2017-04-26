package org.openmrs.module.fhir.mapper.model;


import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Resource;

import java.util.List;

public class FHIRResource {
    private String resourceName;
    private List<Identifier> identifierList;
    private Resource resource;

    public FHIRResource(String resourceName, List<Identifier> identifierList, Resource resource) {
        this.resourceName = resourceName;
        this.identifierList = identifierList;
        this.resource = resource;
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<Identifier> getIdentifierList() {
        return identifierList;
    }

    public Resource getResource() {
        return resource;
    }

    public Identifier getIdentifier() {
        if ((identifierList != null) && !identifierList.isEmpty()) {
            return identifierList.get(0);
        }
        return null;
    }
}
