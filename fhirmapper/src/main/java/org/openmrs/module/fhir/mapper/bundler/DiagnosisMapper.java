package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IResource;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.openmrs.module.fhir.FHIRProperties.PREVIOUS_CONDITION_EXTENSION_NAME;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.mapper.model.ObservationType.VISIT_DIAGNOSES;

@Component("fhirDiagnosisMapper")
public class DiagnosisMapper implements EmrObsResourceHandler {

    @Autowired
    private CodeableConceptService codeableConceptService;
    @Autowired
    private IdMappingRepository idMappingRepository;

    private final Map<String, Condition.ConditionVerificationStatus> diaConditionStatus = new HashMap<>();

    public DiagnosisMapper() {
        diaConditionStatus.put(MRSProperties.MRS_DIAGNOSIS_STATUS_PRESUMED, Condition.ConditionVerificationStatus.PROVISIONAL);
        diaConditionStatus.put(MRSProperties.MRS_DIAGNOSIS_STATUS_CONFIRMED, Condition.ConditionVerificationStatus.CONFIRMED);
    }

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        return obs.isOfType(VISIT_DIAGNOSES);
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> diagnoses = new ArrayList<>();

        FHIRResource fhirCondition = createFHIRCondition(obs, fhirEncounter, systemProperties);
        if (fhirCondition != null) {
            diagnoses.add(fhirCondition);
        }
        return diagnoses;
    }

    private FHIRResource createFHIRCondition(Obs visitDiagnosisObs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        Condition condition = new Condition();
        condition.setContext(new Reference().setReference(fhirEncounter.getId()));
        condition.setSubject(fhirEncounter.getPatient());
        setAsserter(fhirEncounter, condition);
        condition.setCategory(setDiagnosisCategory(systemProperties));

        final CompoundObservation visitDiagnosisObservation = new CompoundObservation(visitDiagnosisObs);
        Obs codedDiagnosisObs = visitDiagnosisObservation.getMemberObsForConceptName(MRSProperties.MRS_CONCEPT_NAME_CODED_DIAGNOSIS);
        if (codedDiagnosisObs == null) return null;
        setConditionCode(condition, codedDiagnosisObs);
        if (condition.getCode().isEmpty()) return null;
        setConditionVerificationStatus(condition, visitDiagnosisObservation);
        setPreviousDiagnosisExtension(condition, visitDiagnosisObservation);

        setId(visitDiagnosisObs, systemProperties, condition);
        condition.setNote(asList(new Annotation(new StringType(visitDiagnosisObs.getComment()))));
        return new FHIRResource(MRSProperties.TR_CONDITION_CODE_DIAGNOSIS_DISPLAY, condition.getIdentifier(), condition);
    }

    private List<CodeableConcept> setDiagnosisCategory(SystemProperties systemProperties) {
        CodeableConcept codeableConcept = new CodeableConcept();
        String valuesetUrl = systemProperties.createValueSetUrlFor(TR_CONDITION_CATEGORY_VALUESET_NAME);
        codeableConcept.addCoding().setSystem(valuesetUrl).setCode(TR_CONDITION_CATEGORY_DIAGNOSIS_CODE);
        return asList(codeableConcept);
    }

    private void setPreviousDiagnosisExtension(Condition condition, CompoundObservation visitDiagnosisObservation) {
        final Obs initialDiagnosisObs = visitDiagnosisObservation.getMemberObsForConceptName(MRS_CONCEPT_NAME_INITIAL_DIAGNOSIS);
        final String initialDiagnosisUuid = initialDiagnosisObs.getValueText();
        if (visitDiagnosisObservation.getRawObservation().getUuid().equals(initialDiagnosisUuid)) return;
        final String previousDiagnosisUri = getPreviousDiagnosisUri(initialDiagnosisUuid);
        String fhirExtensionUrl = FHIRProperties.getFhirExtensionUrl(PREVIOUS_CONDITION_EXTENSION_NAME);
        condition.addExtension().setUrl(fhirExtensionUrl).setValue(new StringType(previousDiagnosisUri));
    }


    private String getPreviousDiagnosisUri(String initialDiagnosisUuid) {
        IdMapping diagnosisIdMapping = idMappingRepository.findByInternalId(initialDiagnosisUuid, IdMappingType.DIAGNOSIS);
        if (diagnosisIdMapping == null) {
            throw new RuntimeException("Previous diagnosis with id [" + initialDiagnosisUuid + "] is not synced to SHR yet.");
        }
        return diagnosisIdMapping.getUri();
    }

    private void setConditionVerificationStatus(Condition condition, CompoundObservation visitDiagnosisObservation) {
        Obs diagnosisCertainityObs = visitDiagnosisObservation.getMemberObsForConceptName(MRSProperties.MRS_CONCEPT_NAME_DIAGNOSIS_CERTAINTY);
        condition.setVerificationStatus(getConditionStatus(diagnosisCertainityObs));
    }

    private void setConditionCode(Condition condition, Obs codedDiagnosisObs) {
        CodeableConcept diagnosisCode = codeableConceptService.addTRCoding(codedDiagnosisObs.getValueCoded());
        condition.setCode(diagnosisCode);
    }

    private void setAsserter(FHIREncounter fhirEncounter, Condition condition) {
        Reference participant = fhirEncounter.getFirstParticipantReference();
        if (null != participant) {
            condition.setAsserter(participant);
        }
    }

    private void setId(Obs visitDiagnosisObs, SystemProperties systemProperties, Condition condition) {
        Identifier identifier = condition.addIdentifier();
        String diagnosisResourceId = new EntityReference().build(IResource.class, systemProperties, visitDiagnosisObs.getUuid());
        identifier.setValue(diagnosisResourceId);
        condition.setId(diagnosisResourceId);
    }


    private Condition.ConditionVerificationStatus getConditionStatus(Obs member) {
        Concept diagnosisStatus = member.getValueCoded();
        Condition.ConditionVerificationStatus status = diaConditionStatus.get(diagnosisStatus.getName().getName());
        return status != null ? status : Condition.ConditionVerificationStatus.CONFIRMED;
    }
}
