package org.openmrs.module.shrclient.web.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sun.syndication.feed.atom.Category;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Composition;
import org.openmrs.module.fhir.mapper.model.EntityReference;

import java.util.List;

import static org.openmrs.module.fhir.utils.FHIRBundleHelper.getComposition;

public class EncounterEvent {
    public static final String LATEST_UPDATE_CATEGORY_TAG = "latest_update_event_id";
    public static final String ENCOUNTER_UPDATED_CATEGORY_TAG = "encounter_updated_at";

    @JsonProperty("id")
    private String eventId;
    private String healthId;
    private String publishedDate;

    @JsonProperty("title")
    private String title;

    @JsonProperty("link")
    private String link;

    @JsonProperty("categories")
    private List categories;

    @JsonProperty("content")
    @JsonDeserialize(using = BundleDeserializer.class)
    private Bundle bundle;
    private String encounterId;

    public String getEncounterId() {
        return encounterId;
    }

    public String getHealthId() {
        return healthId;
    }

    public String getPublishedDate() {
        return publishedDate;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public void setPublishedDate(String date) {
        this.publishedDate = date;
    }

    public void addContent(Bundle bundle) {
        this.bundle = bundle;
        this.healthId = identifyPatientHealthId(bundle);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.encounterId = StringUtils.substringAfter(title, "Encounter:");
    }

    public void setHealthId(String healthId){
        this.healthId = healthId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public List getCategories() {
        return categories;
    }

    public void setCategories(List categories) {
        this.categories = categories;
    }

    public Category getLatestUpdateEventCategory() {
        return getCategoryByTerm(LATEST_UPDATE_CATEGORY_TAG);
    }

    public Category getEncounterUpdatedCategory() {
        return getCategoryByTerm(ENCOUNTER_UPDATED_CATEGORY_TAG);
    }

    private Category getCategoryByTerm(String categoryTag) {
        List allCategories = getCategories();
        if (allCategories != null) {
            for (Object category : allCategories) {
                if (((Category) category).getTerm().startsWith(categoryTag))
                    return (Category) category;
            }
        }
        return null;
    }

    private String identifyPatientHealthId(Bundle bundle) {
        final Composition composition = getComposition(bundle);
        return new EntityReference().parse(org.openmrs.Patient.class, composition.getSubject().getReference());
    }
}
