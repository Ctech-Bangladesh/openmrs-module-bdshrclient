package org.openmrs.module.fhir.mapper.bundler;


import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.mapper.model.*;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class ProcedureMapper implements EmrObsResourceHandler {

    @Autowired
    private CodeableConceptService codeableConceptService;

    @Autowired
    private DiagnosticReportBuilder diagnosticReportBuilder;

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private ObservationValueMapper observationValueMapper;

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        return obs.isOfType(ObservationType.PROCEDURES);
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        CompoundObservation compoundObservationProcedure = new CompoundObservation(obs);
        return mapProcedure(obs, fhirEncounter, systemProperties, compoundObservationProcedure);
    }

    private List<FHIRResource> mapProcedure(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties, CompoundObservation compoundObservationProcedure) {
        List<FHIRResource> resources = new ArrayList<>();
        Procedure procedure = new Procedure();

        procedure.setSubject(fhirEncounter.getPatient());
        procedure.setContext(new Reference().setReference(fhirEncounter.getId()));
        CodeableConcept procedureType = getProcedure(compoundObservationProcedure);
        if (procedureType != null) {
            procedure.setCode(procedureType);
            setIdentifier(obs, systemProperties, procedure);
            procedure.setOutcome(getProcedureOutcome(compoundObservationProcedure, systemProperties));
            procedure.setFollowUp(getProcedureFollowUp(compoundObservationProcedure));
            procedure.setStatus(getProcedureStatus(compoundObservationProcedure));
            procedure.setNote(getProcedureNotes(compoundObservationProcedure));
            procedure.setPerformed(getProcedurePeriod(compoundObservationProcedure));
            setPerformers(fhirEncounter, procedure);
            addReportToProcedure(compoundObservationProcedure, fhirEncounter, systemProperties, procedure, resources);
            FHIRResource procedureResource = new FHIRResource("Procedure", procedure.getIdentifier(), procedure);
            resources.add(procedureResource);
        }
        return resources;
    }

    private void setPerformers(FHIREncounter fhirEncounter, Procedure procedure) {
        List<Encounter.EncounterParticipantComponent> participants = fhirEncounter.getEncounter().getParticipant();
        for (Encounter.EncounterParticipantComponent participant : participants) {
            Procedure.ProcedurePerformerComponent performer = new Procedure.ProcedurePerformerComponent();
            performer.setActor(participant.getIndividual());
            procedure.addPerformer(performer);
        }
    }

    private Procedure.ProcedureStatus getProcedureStatus(CompoundObservation procedure) {
        Obs procdureStatusObs = procedure.getMemberObsForConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_STATUS));
        if (procdureStatusObs != null) {
            String statusCode = codeableConceptService.getTRValueSetCode(procdureStatusObs.getValueCoded());
            try {
                return Procedure.ProcedureStatus.fromCode(statusCode);
            } catch (FHIRException e) {
                return Procedure.ProcedureStatus.COMPLETED;
            }
        }
        return Procedure.ProcedureStatus.COMPLETED;
    }

    private List<Annotation> getProcedureNotes(CompoundObservation procedure) {
        List<Obs> notesObses = procedure.getAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_NOTES);
        ArrayList<Annotation> annotationDts = new ArrayList<>();
        for (Obs notesObs : notesObses) {
            annotationDts.add(new Annotation().setText(notesObs.getValueText()));
        }
        return annotationDts.isEmpty() ? null : annotationDts;
    }

    private void addReportToProcedure(CompoundObservation compoundObservationProcedure, FHIREncounter fhirEncounter, SystemProperties systemProperties, Procedure procedure, List<FHIRResource> allResources) {
        List<Obs> diagnosticStudyObses = compoundObservationProcedure.getAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_STUDY);
        for (Obs diagnosticStudyObs : diagnosticStudyObses) {
            DiagnosticReport diagnosticReport = buildDiagnosticReport(new CompoundObservation(diagnosticStudyObs), fhirEncounter, systemProperties, allResources);
            if (diagnosticReport != null) {
                FHIRResource diagnosticReportResource = new FHIRResource("Diagnostic Report", diagnosticReport.getIdentifier(), diagnosticReport);
                Reference diagnosticResourceRef = procedure.addReport();
                diagnosticResourceRef.setReference(diagnosticReportResource.getIdentifier().getValue());
                diagnosticResourceRef.setDisplay(diagnosticReportResource.getResourceName());
                allResources.add(diagnosticReportResource);
            }
        }
    }

    private CodeableConcept getProcedure(CompoundObservation compoundObservationProcedure) {
        CodeableConcept procedureType = null;
        Obs procedureTypeObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_TYPE);
        if (procedureTypeObs != null) {
            Concept valueCoded = procedureTypeObs.getValueCoded();
            procedureType = codeableConceptService.addTRCodingOrDisplay(valueCoded);
        }
        return procedureType != null && !procedureType.isEmpty() ? procedureType : null;
    }

    private void setIdentifier(Obs obs, SystemProperties systemProperties, Procedure procedure) {
        String id = new EntityReference().build(Obs.class, systemProperties, obs.getUuid());
        procedure.addIdentifier().setValue(id);
        procedure.setId(id);
    }

    private CodeableConcept getProcedureOutcome(CompoundObservation compoundObservationProcedure, SystemProperties systemProperties) {
        Concept outcomeConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_OUTCOME);
        Obs outcomeObs = compoundObservationProcedure.getMemberObsForConcept(outcomeConcept);
        if (outcomeObs != null) {
            return codeableConceptService.getTRValueSetCodeableConcept(outcomeObs.getValueCoded(), TrValueSetType.PROCEDURE_OUTCOME.getTrPropertyValueSetUrl(systemProperties));
        }
        return null;
    }

    private List<CodeableConcept> getProcedureFollowUp(CompoundObservation compoundObservationProcedure) {
        List<Obs> followupObses = compoundObservationProcedure.getAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_FOLLOWUP);
        List<CodeableConcept> followUpCodeableConcepts = new ArrayList<>();
        for (Obs followupObs : followupObses) {
            followUpCodeableConcepts.add(codeableConceptService.addTRCodingOrDisplay(followupObs.getValueCoded()));
        }
        return followUpCodeableConcepts.isEmpty() ? null : followUpCodeableConcepts;
    }

    private Period getProcedurePeriod(CompoundObservation compoundObservationProcedure) {
        Obs startDateObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_START_DATE);
        Obs endDateObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_END_DATE);
        return getPeriod(startDateObs, endDateObs);
    }

    private Period getPeriod(Obs startDateObs, Obs endDateObs) {
        Period period = new Period();
        if (startDateObs != null) period.setStart(startDateObs.getValueDate(), TemporalPrecisionEnum.MILLI);
        if (startDateObs != null && endDateObs != null)
            period.setEnd(endDateObs.getValueDate(), TemporalPrecisionEnum.MILLI);
        return period;
    }

    private DiagnosticReport buildDiagnosticReport(CompoundObservation diagnosticStudyObs, FHIREncounter fhirEncounter, SystemProperties systemProperties, List<FHIRResource> allResources) {
        CodeableConcept diagnosisTestName = getNameToDiagnosticReport(diagnosticStudyObs);
        if (diagnosisTestName != null) {
            DiagnosticReport diagnosticReport = diagnosticReportBuilder.build(diagnosticStudyObs.getRawObservation(), fhirEncounter, systemProperties);
            diagnosticReport.setCode(diagnosisTestName);
            addCategoryToReport(diagnosticReport);
            addDiagnosticResults(diagnosticStudyObs, systemProperties, allResources, diagnosticReport);
            addDiagnosisToDiagnosticReport(diagnosticReport, diagnosticStudyObs);
            return diagnosticReport;
        }
        return null;
    }

    private void addCategoryToReport(DiagnosticReport diagnosticReport) {
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
                .setSystem(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL)
                .setCode(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_CODE)
                .setDisplay(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_OTHER_DISPLAY    );
        diagnosticReport.setCategory(category);
    }

    private void addDiagnosticResults(CompoundObservation diagnosticStudyObs, SystemProperties systemProperties, List<FHIRResource> allResources, DiagnosticReport diagnosticReport) {
        for (Obs diagnosticResultObs : diagnosticStudyObs.getAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT)) {
            FHIRResource resultObservation = getResultObservationResource(diagnosticResultObs, diagnosticReport, systemProperties);
            if (resultObservation != null) {
                diagnosticReport.addResult()
                        .setReference(resultObservation.getResource().getId())
                        .setDisplay(resultObservation.getResourceName());
            }
            allResources.add(resultObservation);
        }
    }

    private void addDiagnosisToDiagnosticReport(DiagnosticReport diagnosticReport, CompoundObservation compoundDiagnosticStudyObs) {
        List<Obs> diagnosisObses = compoundDiagnosticStudyObs.getAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSIS);
        for (Obs diagnosisObs : diagnosisObses) {
            CodeableConcept codeableType = codeableConceptService.addTRCodingOrDisplay(diagnosisObs.getValueCoded());
            if (codeableType != null && !codeableType.isEmpty()) {
                CodeableConcept codedDiagnosis = diagnosticReport.addCodedDiagnosis();
                codedDiagnosis.getCoding().addAll(codeableType.getCoding());
            }
        }
    }

    private FHIRResource getResultObservationResource(Obs diagnosticResultObs, DiagnosticReport diagnosticReport, SystemProperties systemProperties) {
        Observation observation = buildResultObservation(diagnosticResultObs, diagnosticReport, systemProperties);
        String diagnosticTestName = diagnosticReport.getCode().getCoding().get(0).getDisplay();
        return new FHIRResource(diagnosticTestName, observation.getIdentifier(), observation);
    }

    private Observation buildResultObservation(Obs diagnosticResultObs, DiagnosticReport diagnosticReport, SystemProperties systemProperties) {
        Observation observation = new Observation();
        String id = new EntityReference().build(Resource.class, systemProperties, diagnosticResultObs.getUuid());
        observation.addIdentifier().setValue(id);
        observation.setId(id);
        observation.setSubject(diagnosticReport.getSubject());
        observation.setContext(diagnosticReport.getContext());
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setCode(diagnosticReport.getCode());
        observation.setValue(observationValueMapper.map(diagnosticResultObs));
        return observation;
    }

    private CodeableConcept getNameToDiagnosticReport(CompoundObservation compoundDiagnosticStudyObs) {
        CodeableConcept name = null;
        if (compoundDiagnosticStudyObs.getRawObservation() != null) {
            Obs diagnosticTestObs = compoundDiagnosticStudyObs.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_TEST);
            name = diagnosticTestObs != null ? codeableConceptService.addTRCodingOrDisplay(diagnosticTestObs.getValueCoded()) : null;
        }
        return name != null && name.isEmpty() ? null : name;
    }
}
