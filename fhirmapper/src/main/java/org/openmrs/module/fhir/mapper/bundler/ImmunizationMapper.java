package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.*;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class ImmunizationMapper implements EmrObsResourceHandler {

    @Autowired
    private IdMappingRepository idMappingsRepository;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        return obs.isOfType(ObservationType.IMMUNIZATION);
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> resources = new ArrayList<>();
        CompoundObservation immunizationTemplateObs = new CompoundObservation(obs);
        List<Obs> allObsForConceptName = immunizationTemplateObs.getAllMemberObsForConceptName(MRS_CONCEPT_IMMUNIZATION_INCIDENT_GROUP);
        if (allObsForConceptName.isEmpty()) return new ArrayList<>();

        for (Obs immunizationGroupObs : allObsForConceptName) {
            Immunization immunization = createImmunizationResource(new CompoundObservation(immunizationGroupObs), fhirEncounter, systemProperties);
            if (immunization != null) {
                FHIRResource immunizationResource = new FHIRResource("Immunization", immunization.getIdentifier(), immunization);
                resources.add(immunizationResource);
            }
        }
        return resources;
    }

    private Immunization createImmunizationResource(CompoundObservation immunizationIncidentObs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        Immunization immunization = new Immunization();
        immunization.setPatient(fhirEncounter.getPatient());
        immunization.setEncounter(new Reference().setReference(fhirEncounter.getId()));
        Obs vaccineObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_VACCINE);
        List<Drug> drugs = identifyDrugs(vaccineObs);
        if (CollectionUtils.isEmpty(drugs)) {
            return null;
        }
        immunization.setStatus(getImmunizationStatus(immunizationIncidentObs));
        immunization.setVaccineCode(getVaccineCode(drugs));
        setIdentifier(immunizationIncidentObs, systemProperties, immunization);
        immunization.setDate(getVaccinationDate(immunizationIncidentObs));
        immunization.setNotGiven(getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REFUSED));
        immunization.addPractitioner().setActor(fhirEncounter.getFirstParticipantReference());
        // need to set role
        immunization.setPrimarySource(!getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REPORTED));
        immunization.setDoseQuantity(getDosage(immunizationIncidentObs, systemProperties));
        immunization.setExplanation(getExplation(immunizationIncidentObs, systemProperties));
        setRoute(immunizationIncidentObs, immunization, systemProperties);
        setNotes(immunizationIncidentObs, immunization);

        return immunization;
    }

    private void setNotes(CompoundObservation immunizationIncidentObs, Immunization immunization) {
        List<Obs> notesObsList = immunizationIncidentObs.getAllMemberObsForConceptName(MRS_CONCEPT_IMMUNIZATION_NOTE);
        for (Obs obs : notesObsList) {
            immunization.addNote().setText(obs.getValueText());
        }
    }

    private List<Drug> identifyDrugs(Obs vaccineObs) {
        Drug vaccineDrug = vaccineObs.getValueDrug();
        Concept vaccineConcept = vaccineObs.getValueCoded();
        if (vaccineDrug != null) {
            return Arrays.asList(vaccineDrug);
        } else {
            return conceptService.getDrugsByConcept(vaccineConcept);
        }
    }

    private Immunization.ImmunizationStatus getImmunizationStatus(CompoundObservation immunizationIncidentObs) {
        Obs statusObs = immunizationIncidentObs.getMemberObsForConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_STATUS));
        if (statusObs != null) {
            String trValueSetCode = codeableConceptService.getTRValueSetCode(statusObs.getValueCoded());
            try {
                return Immunization.ImmunizationStatus.fromCode(trValueSetCode);
            } catch (FHIRException e) {
                return Immunization.ImmunizationStatus.COMPLETED;
            }
        }
        return Immunization.ImmunizationStatus.COMPLETED;
    }

    private void setRoute(CompoundObservation immunizationIncidentObs, Immunization immunization, SystemProperties systemProperties) {
        Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
        Obs routeObs = immunizationIncidentObs.getMemberObsForConceptName(routeOfAdministrationConcept.getName().getName());
        if (routeObs != null) {
            CodeableConcept codeableConceptDt = codeableConceptService.getTRValueSetCodeableConcept(routeObs.getValueCoded(),
                    TrValueSetType.ROUTE_OF_ADMINISTRATION.getTrPropertyValueSetUrl(systemProperties),
                    new CodeableConcept());
            CodeableConcept routeCodeableConcept = new CodeableConcept();
            routeCodeableConcept.setCoding(codeableConceptDt.getCoding());
            immunization.setRoute(routeCodeableConcept);
        }
    }

    private Immunization.ImmunizationExplanationComponent getExplation(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Immunization.ImmunizationExplanationComponent explanationComponent = new Immunization.ImmunizationExplanationComponent();
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REASON);
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REFUSAL_REASON);
        return hasNoReasons(explanationComponent) ? null : explanationComponent;
    }

    private boolean hasNoReasons(Immunization.ImmunizationExplanationComponent explanationComponent) {
        return CollectionUtils.isEmpty(explanationComponent.getReason()) && CollectionUtils.isEmpty(explanationComponent.getReasonNotGiven());
    }

    private void populateReason(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties,
                                Immunization.ImmunizationExplanationComponent explanationComponent,
                                TrValueSetType trValueSetType) {
        Concept reasonConcept = omrsConceptLookup.findTRConceptOfType(trValueSetType);
        Obs immunizationReasonObs = immunizationIncidentObs.getMemberObsForConceptName(reasonConcept.getName().getName());
        if (immunizationReasonObs != null && idMappingsRepository.findByInternalId(immunizationReasonObs.getValueCoded().getUuid(), IdMappingType.CONCEPT) != null) {
            CodeableConcept reason = codeableConceptService.getTRValueSetCodeableConcept(immunizationReasonObs.getValueCoded(),
                    trValueSetType.getTrPropertyValueSetUrl(systemProperties));
            setReason(trValueSetType, explanationComponent, reason);
        }
    }

    private void setReason(TrValueSetType trValueSetType, Immunization.ImmunizationExplanationComponent  explanationComponent, CodeableConcept reason) {
        if (TrValueSetType.IMMUNIZATION_REFUSAL_REASON.equals(trValueSetType)) {
            explanationComponent.addReasonNotGiven(reason);
        } else {
            CodeableConcept conceptDt = explanationComponent.addReason();
            conceptDt.setCoding(reason.getCoding());
        }
    }

    private SimpleQuantity getDosage(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Obs doseObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_DOSAGE);
        if (doseObs == null) return null;
        SimpleQuantity dose = new SimpleQuantity();
        dose.setValue(doseObs.getValueNumeric());
        populateQuantityUnits(immunizationIncidentObs, systemProperties, dose);
        return dose;
    }

    private void populateQuantityUnits(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties, Quantity dose) {
        Concept quantityUnitsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.QUANTITY_UNITS);
        Obs quantityUnitsObs = immunizationIncidentObs.getMemberObsForConceptName(quantityUnitsConcept.getName().getName());
        if (quantityUnitsObs != null) {
            dose.setCode(codeableConceptService.getTRValueSetCode(quantityUnitsObs.getValueCoded()));
            if (idMappingsRepository.findByInternalId(quantityUnitsObs.getValueCoded().getUuid(), IdMappingType.CONCEPT) != null)
                dose.setSystem(TrValueSetType.QUANTITY_UNITS.getTrPropertyValueSetUrl(systemProperties));
        }
    }

    private void setIdentifier(CompoundObservation obs, SystemProperties systemProperties, Immunization immunization) {
        Identifier identifier = immunization.addIdentifier();
        String immunizationId = new EntityReference().build(Immunization.class, systemProperties, obs.getUuid());
        identifier.setValue(immunizationId);
        immunization.setId(immunizationId);
    }

    private Date getVaccinationDate(CompoundObservation immunizationIncidentObs) {
        Obs vaccinationDateObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_VACCINATION_DATE);
        if (vaccinationDateObs == null) return null;
        return vaccinationDateObs.getValueDate();
    }

    private CodeableConcept getVaccineCode(List<Drug> drugs) {
        Drug drugsByConcept = drugs.get(0);
        IdMapping idMapping = idMappingsRepository.findByInternalId(drugsByConcept.getUuid(), IdMappingType.MEDICATION);
        CodeableConcept codeableConcept = new CodeableConcept();
        if (idMapping != null) {
            codeableConceptService.addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), drugsByConcept.getDisplayName());
        } else {
            Coding coding = codeableConcept.addCoding();
            coding.setDisplay(drugsByConcept.getDisplayName());
        }
        return codeableConcept;
    }

    private Boolean getIndicator(CompoundObservation immunizationIncidentObs, String conceptName) {
        Obs indicatorObs = immunizationIncidentObs.getMemberObsForConceptName(conceptName);
        if (indicatorObs != null) {
            return indicatorObs.getValueAsBoolean();
        }
        return false;
    }
}
