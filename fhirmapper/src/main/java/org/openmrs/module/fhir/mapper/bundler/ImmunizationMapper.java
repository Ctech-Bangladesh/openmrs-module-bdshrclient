package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.BoundCodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Immunization;
import ca.uhn.fhir.model.dstu2.valueset.ImmunizationRouteCodesEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.openmrs.Concept;
import org.openmrs.Drug;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ObservationType;
import org.openmrs.module.fhir.utils.CodableConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private CodableConceptService codableConceptService;
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
        immunization.setEncounter(new ResourceReferenceDt().setReference(new EntityReference().build(Encounter.class, systemProperties, fhirEncounter.getId().getValueAsString())));
        Obs vaccineObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_VACCINE);
        List<Drug> drugs = conceptService.getDrugsByConcept(vaccineObs.getValueCoded());
        if (CollectionUtils.isEmpty(drugs)) {
            return null;
        }
        immunization.setVaccineType(getVaccineType(drugs));
        setIdentifier(immunizationIncidentObs, systemProperties, immunization);
        immunization.setDate(getVaccinationDate(immunizationIncidentObs), TemporalPrecisionEnum.MILLI);
        immunization.setWasNotGiven(getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REFUSED));
        immunization.setRequester(getRequester(fhirEncounter));
        immunization.setReported(getIndicator(immunizationIncidentObs, MRS_CONCEPT_VACCINATION_REPORTED));
        immunization.setDoseQuantity(getDosage(immunizationIncidentObs, systemProperties));
        immunization.setExplanation(getExplation(immunizationIncidentObs, systemProperties));
        immunization.setRoute(getRoute(immunizationIncidentObs, systemProperties));

        return immunization;
    }

    private BoundCodeableConceptDt<ImmunizationRouteCodesEnum> getRoute(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
        Obs routeObs = immunizationIncidentObs.getMemberObsForConceptName(routeOfAdministrationConcept.getName().getName());
        if (routeObs != null) {
            return (BoundCodeableConceptDt<ImmunizationRouteCodesEnum>) codableConceptService.getTRValueSetCodeableConcept(routeObs.getValueCoded(), systemProperties.getTrValuesetUrl(PropertyKeyConstants.TR_VALUESET_ROUTE), new BoundCodeableConceptDt<ImmunizationRouteCodesEnum>(ImmunizationRouteCodesEnum.VALUESET_BINDER));
        }
        return null;
    }

    private Immunization.Explanation getExplation(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Immunization.Explanation explanationComponent = new Immunization.Explanation();
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REASON, PropertyKeyConstants.TR_VALUESET_IMMUNIZATION_REASON);
        populateReason(immunizationIncidentObs, systemProperties, explanationComponent, TrValueSetType.IMMUNIZATION_REFUSAL_REASON, PropertyKeyConstants.TR_VALUESET_REFUSAL_REASON);
        return hasNoReasons(explanationComponent) ? null : explanationComponent;
    }

    private boolean hasNoReasons(Immunization.Explanation explanationComponent) {
        return CollectionUtils.isEmpty(explanationComponent.getReason()) && CollectionUtils.isEmpty(explanationComponent.getReasonNotGiven());
    }

    private void populateReason(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties,
                                Immunization.Explanation explanationComponent,
                                TrValueSetType trValueSetType, String valueSetKeyName) {
        Concept reasonConcept = omrsConceptLookup.findTRConceptOfType(trValueSetType);
        Obs immunizationReasonObs = immunizationIncidentObs.getMemberObsForConceptName(reasonConcept.getName().getName());
        if (immunizationReasonObs != null && idMappingsRepository.findByInternalId(immunizationReasonObs.getValueCoded().getUuid()) != null) {
            CodeableConceptDt reason = getReason(trValueSetType, explanationComponent);
            codableConceptService.getTRValueSetCodeableConcept(immunizationReasonObs.getValueCoded(),
                    systemProperties.getTrValuesetUrl(valueSetKeyName), reason);
        }
    }

    private CodeableConceptDt getReason(TrValueSetType trValueSetType, Immunization.Explanation explanationComponent) {
        return TrValueSetType.IMMUNIZATION_REFUSAL_REASON.equals(trValueSetType) ? explanationComponent.addReasonNotGiven() : explanationComponent.addReason();
    }

    private QuantityDt getDosage(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties) {
        Obs doseObs = immunizationIncidentObs.getMemberObsForConceptName(MRS_CONCEPT_DOSAGE);
        if (doseObs == null) return null;
        QuantityDt dose = new QuantityDt();
        dose.setValue(doseObs.getValueNumeric());
        populateQuantityUnits(immunizationIncidentObs, systemProperties, dose);
        return dose;
    }

    private void populateQuantityUnits(CompoundObservation immunizationIncidentObs, SystemProperties systemProperties, QuantityDt dose) {
        Concept quantityUnitsConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.QUANTITY_UNITS);
        Obs quantityUnitsObs = immunizationIncidentObs.getMemberObsForConceptName(quantityUnitsConcept.getName().getName());
        if (quantityUnitsObs != null) {
            dose.setCode(codableConceptService.getTRValueSetCode(quantityUnitsObs.getValueCoded()));
            if (idMappingsRepository.findByInternalId(quantityUnitsObs.getValueCoded().getUuid()) != null)
                dose.setSystem(systemProperties.getTrValuesetUrl(PropertyKeyConstants.TR_VALUESET_QTY_UNITS));
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

    private CodeableConceptDt getVaccineType(List<Drug> drugs) {
        Drug drugsByConcept = drugs.get(0);
        IdMapping idMapping = idMappingsRepository.findByInternalId(drugsByConcept.getUuid());
        CodeableConceptDt codeableConcept = new CodeableConceptDt();
        if (idMapping != null) {
            codableConceptService.addFHIRCoding(codeableConcept, idMapping.getExternalId(), idMapping.getUri(), drugsByConcept.getDisplayName());
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
