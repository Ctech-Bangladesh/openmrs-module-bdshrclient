package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.BoundCodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.composite.SimpleQuantityDt;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Immunization;
import ca.uhn.fhir.model.dstu2.valueset.ImmunizationReasonCodesEnum;
import ca.uhn.fhir.model.dstu2.valueset.ImmunizationRouteCodesEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ObservationType;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@Component
public class ImmunizationMapper implements EmrObsResourceHandler {

    @Autowired
    private IdMappingsRepository idMappingsRepository;
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
    public List<FHIRResource> map(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> resources = new ArrayList<>();
        Immunization immunization = createImmunizationResource(new CompoundObservation(obs), fhirEncounter, systemProperties);
        if (immunization != null) {
            FHIRResource immunizationResource = new FHIRResource("Immunization", immunization.getIdentifier(), immunization);
            resources.add(immunizationResource);
        }
        return resources;
    }

    private Immunization createImmunizationResource(CompoundObservation immunizationIncidentObs, Encounter fhirEncounter, SystemProperties systemProperties) {
        Immunization immunization = new Immunization();
        immunization.setPatient(fhirEncounter.getPatient());
        immunization.setEncounter(new ResourceReferenceDt().setReference(fhirEncounter.getId().getValue()));
        Obs vaccineObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_VACCINE);
        List<Drug> drugs = identifyDrugs(vaccineObs);
        if (CollectionUtils.isEmpty(drugs)) {
            return null;
        }
        immunization.setStatus(getImmunizationStatus(immunizationIncidentObs));
        immunization.setVaccineCode(getVaccineCode(drugs));
        setIdentifier(immunizationIncidentObs, systemProperties, immunization);
        immunization.setDate(getVaccinationDate(immunizationIncidentObs), TemporalPrecisionEnum.MILLI);
        immunization.setWasNotGiven(getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REFUSED));
        immunization.setRequester(getRequester(fhirEncounter));
        immunization.setReported(getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REPORTED));
        immunization.setDoseQuantity(getDosage(immunizationIncidentObs, systemProperties));
        immunization.setExplanation(getExplation(immunizationIncidentObs, systemProperties));
        setRoute(immunizationIncidentObs, immunization, systemProperties);

        return immunization;
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

    private String getImmunizationStatus(CompoundObservation immunizationIncidentObs) {
        Obs procdureStatusObs = immunizationIncidentObs.getMemberObsForConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.IMMUNIZATION_STATUS));
        if (procdureStatusObs != null) {
            return codeableConceptService.getTRValueSetCode(procdureStatusObs.getValueCoded());
        }
        return "completed";
    }

    private void setRoute(CompoundObservation immunizationIncidentObs, Immunization immunization, SystemProperties systemProperties) {
        Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
        Obs routeObs = immunizationIncidentObs.getMemberObsForConceptName(routeOfAdministrationConcept.getName().getName());
        if (routeObs != null) {
            CodeableConceptDt codeableConceptDt = codeableConceptService.getTRValueSetCodeableConcept(routeObs.getValueCoded(), TrValueSetType.ROUTE_OF_ADMINISTRATION.getTrPropertyValueSetUrl(systemProperties), new BoundCodeableConceptDt<>(ImmunizationRouteCodesEnum.VALUESET_BINDER));
            BoundCodeableConceptDt<ImmunizationRouteCodesEnum> routeCodeableConcept = new BoundCodeableConceptDt<>(ImmunizationRouteCodesEnum.VALUESET_BINDER);
            routeCodeableConcept.setCoding(codeableConceptDt.getCoding());
            immunization.setRoute(routeCodeableConcept);
        }
    }

    private Immunization.Explanation getExplation(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Immunization.Explanation explanationComponent = new Immunization.Explanation();
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REASON);
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REFUSAL_REASON);
        return hasNoReasons(explanationComponent) ? null : explanationComponent;
    }

    private boolean hasNoReasons(Immunization.Explanation explanationComponent) {
        return CollectionUtils.isEmpty(explanationComponent.getReason()) && CollectionUtils.isEmpty(explanationComponent.getReasonNotGiven());
    }

    private void populateReason(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties,
                                Immunization.Explanation explanationComponent,
                                TrValueSetType trValueSetType) {
        Concept reasonConcept = omrsConceptLookup.findTRConceptOfType(trValueSetType);
        Obs immunizationReasonObs = immunizationIncidentObs.getMemberObsForConceptName(reasonConcept.getName().getName());
        if (immunizationReasonObs != null && idMappingsRepository.findByInternalId(immunizationReasonObs.getValueCoded().getUuid()) != null) {
            CodeableConceptDt reason = codeableConceptService.getTRValueSetCodeableConcept(immunizationReasonObs.getValueCoded(),
                    trValueSetType.getTrPropertyValueSetUrl(systemProperties));
            setReason(trValueSetType, explanationComponent, reason);
        }
    }

    private void setReason(TrValueSetType trValueSetType, Immunization.Explanation explanationComponent, CodeableConceptDt reason) {
        if(TrValueSetType.IMMUNIZATION_REFUSAL_REASON.equals(trValueSetType)) {
            explanationComponent.addReasonNotGiven(reason);
        } else {
            BoundCodeableConceptDt<ImmunizationReasonCodesEnum> conceptDt = explanationComponent.addReason();
            conceptDt.setCoding(reason.getCoding());
        }
    }

    private SimpleQuantityDt getDosage(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Obs doseObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_DOSAGE);
        if (doseObs == null) return null;
        SimpleQuantityDt dose = new SimpleQuantityDt();
        dose.setValue(doseObs.getValueNumeric());
        populateQuantityUnits(immunizationIncidentObs, systemProperties, dose);
        return dose;
    }

    private void populateQuantityUnits(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties, QuantityDt dose) {
        Concept quantityUnitsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.QUANTITY_UNITS);
        Obs quantityUnitsObs = immunizationIncidentObs.getMemberObsForConceptName(quantityUnitsConcept.getName().getName());
        if (quantityUnitsObs != null) {
            dose.setCode(codeableConceptService.getTRValueSetCode(quantityUnitsObs.getValueCoded()));
            if (idMappingsRepository.findByInternalId(quantityUnitsObs.getValueCoded().getUuid()) != null)
                dose.setSystem(TrValueSetType.QUANTITY_UNITS.getTrPropertyValueSetUrl(systemProperties));
        }
    }

    private ResourceReferenceDt getRequester(Encounter fhirEncounter) {
        List<Encounter.Participant> participants = fhirEncounter.getParticipant();
        return CollectionUtils.isNotEmpty(participants) ? participants.get(0).getIndividual() : null;
    }

    private void setIdentifier(CompoundObservation obs, SystemProperties systemProperties, Immunization immunization) {
        IdentifierDt identifier = immunization.addIdentifier();
        String immunizationId = new EntityReference().build(Immunization.class, systemProperties, obs.getUuid());
        identifier.setValue(immunizationId);
        immunization.setId(immunizationId);
    }

    private Date getVaccinationDate(CompoundObservation immunizationIncidentObs) {
        Obs vaccinationDateObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_VACCINATION_DATE);
        if (vaccinationDateObs == null) return null;
        return vaccinationDateObs.getValueDate();
    }

    private CodeableConceptDt getVaccineCode(List<Drug> drugs) {
        Drug drugsByConcept = drugs.get(0);
        IdMapping idMapping = idMappingsRepository.findByInternalId(drugsByConcept.getUuid());
        CodeableConceptDt codeableConcept = new CodeableConceptDt();
        if (idMapping != null) {
            codeableConceptService.addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), drugsByConcept.getDisplayName());
        } else {
            CodingDt coding = codeableConcept.addCoding();
            coding.setDisplay(drugsByConcept.getDisplayName());
        }
        return codeableConcept;
    }

    private Boolean getIndicator(CompoundObservation immunizationIncidentObs, String conceptName) {
        Obs indicatorObs = immunizationIncidentObs.getMemberObsForConceptName(conceptName);
        if (indicatorObs != null) {
            return indicatorObs.getValueAsBoolean();
        }
        return null;
    }
}
