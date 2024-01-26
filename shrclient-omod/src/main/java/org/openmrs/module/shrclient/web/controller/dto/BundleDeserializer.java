package org.openmrs.module.shrclient.web.controller.dto;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import org.hl7.fhir.dstu3.model.Bundle;
import org.openmrs.module.shrclient.util.FhirBundleContextHolder;

public class BundleDeserializer extends JsonDeserializer<Bundle> {

    @Override
    public Bundle deserialize(JsonParser jp, DeserializationContext ctx) {
        try {
            final String xml = ((TextNode) jp.readValueAsTree()).textValue();
            FhirContext fhirContext = FhirBundleContextHolder.getFhirContext();
            return fhirContext.newXmlParser().parseResource(Bundle.class, xml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
