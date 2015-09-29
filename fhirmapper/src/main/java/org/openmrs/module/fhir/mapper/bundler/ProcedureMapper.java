package org.openmrs.module.fhir.mapper.bundler;


import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.AnnotationDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Procedure;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import ca.uhn.fhir.model.dstu2.valueset.ProcedureStatusEnum;
import ca.uhn.fhir.model.primitive.BoundCodeDt;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.bundler.condition.ObservationValueMapper;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ObservationType;
import org.openmrs.module.fhir.utils.CodableConceptService;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@Component
public class ProcedureMapper implements EmrObsResourceHandler {

    @Autowired
    private CodableConceptService codeableConceptService;

    @Autowired
    private IdMappingsRepository idMappingsRepository;

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
    public List<FHIRResource> map(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        CompoundObservation compoundObservationProcedure = new CompoundObservation(obs);
        return mapProcedure(obs, fhirEncounter, systemProperties, compoundObservationProcedure);
    }

    private List<FHIRResource> mapProcedure(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties, CompoundObservation compoundObservationProcedure) {
        List<FHIRResource> resources = new ArrayList<>();
        Procedure procedure = new Procedure();

        procedure.setSubject(fhirEncounter.getPatient());
        procedure.setEncounter(new ResourceReferenceDt().setReference(fhirEncounter.getId().getValue()));
        CodeableConceptDt procedureType = getProcedure(compoundObservationProcedure);
        if (procedureType != null) {
            procedure.setCode(procedureType);
            setIdentifier(obs, systemProperties, procedure);
            procedure.setOutcome(getProcedureOutcome(compoundObservationProcedure, systemProperties));
            procedure.setFollowUp(getProcedureFollowUp(compoundObservationProcedure, systemProperties));
            procedure.setStatus(getProcedureStatus(compoundObservationProcedure));
            procedure.setNotes(getProcedureNotes(compoundObservationProcedure));
            procedure.setPerformed(getProcedurePeriod(compoundObservationProcedure));
            addReportToProcedure(compoundObservationProcedure, fhirEncounter, systemProperties, procedure, resources);
            FHIRResource procedureResource = new FHIRResource("Procedure", procedure.getIdentifier(), procedure);
            resources.add(procedureResource);
        }
        return resources;
    }

    private BoundCodeDt<ProcedureStatusEnum> getProcedureStatus(CompoundObservation procedure) {
        Obs procdureStatusObs = procedure.getMemberObsForConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_STATUS));
        if (procdureStatusObs != null) {
            String statusCode = codeableConceptService.getTRValueSetCode(procdureStatusObs.getValueCoded());
            BoundCodeDt<ProcedureStatusEnum> code = new BoundCodeDt<>(ProcedureStatusEnum.VALUESET_BINDER);
            code.setValueAsString(statusCode);
            return code;
        }
        return new BoundCodeDt<>(ProcedureStatusEnum.VALUESET_BINDER, ProcedureStatusEnum.COMPLETED);
    }

    private List<AnnotationDt> getProcedureNotes(CompoundObservation procedure) {
        List<Obs> notesObses = procedure.findAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_NOTES);
        ArrayList<AnnotationDt> annotationDts = new ArrayList<>();
        for (Obs notesObs : notesObses) {
            annotationDts.add(new AnnotationDt().setText(notesObs.getValueText()));
        }
        return annotationDts.isEmpty() ? null : annotationDts;
    }

    private void addReportToProcedure(CompoundObservation compoundObservationProcedure, Encounter fhirEncounter, SystemProperties systemProperties, Procedure procedure, List<FHIRResource> allResources) {
        List<Obs> diagnosticStudyObses = compoundObservationProcedure.findAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_STUDY);
        for (Obs diagnosticStudyObs : diagnosticStudyObses) {
            DiagnosticReport diagnosticReport = buildDiagnosticReport(new CompoundObservation(diagnosticStudyObs), fhirEncounter, systemProperties, allResources);
            if (diagnosticReport != null) {
                FHIRResource diagnosticReportResource = new FHIRResource("Diagnostic Report", diagnosticReport.getIdentifier(), diagnosticReport);
                ResourceReferenceDt diagnosticResourceRef = procedure.addReport();
                diagnosticResourceRef.setReference(diagnosticReportResource.getIdentifier().getValue());
                diagnosticResourceRef.setDisplay(diagnosticReportResource.getResourceName());
                allResources.add(diagnosticReportResource);
            }
        }
    }

    private CodeableConceptDt getProcedure(CompoundObservation compoundObservationProcedure) {
        CodeableConceptDt procedureType = null;
        Obs procedureTypeObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_TYPE);
        if (procedureTypeObs != null) {
            Concept valueCoded = procedureTypeObs.getValueCoded();
            procedureType = codeableConceptService.addTRCodingOrDisplay(valueCoded, idMappingsRepository);
        }
        return procedureType != null && !procedureType.isEmpty() ? procedureType : null;
    }

    private void setIdentifier(Obs obs, SystemProperties systemProperties, Procedure procedure) {
        String id = new EntityReference().build(Obs.class, systemProperties, obs.getUuid());
        procedure.addIdentifier().setValue(id);
        procedure.setId(id);
    }

    private CodeableConceptDt getProcedureOutcome(CompoundObservation compoundObservationProcedure, SystemProperties systemProperties) {
        Concept outcomeConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_OUTCOME);
        Obs outcomeObs = compoundObservationProcedure.getMemberObsForConcept(outcomeConcept);
        if (outcomeObs != null) {
            return codeableConceptService.getTRValueSetCodeableConcept(outcomeObs.getValueCoded(), TrValueSetType.PROCEDURE_OUTCOME.getTrPropertyValueSetUrl(systemProperties));
        }
        return null;
    }

    private List<CodeableConceptDt> getProcedureFollowUp(CompoundObservation compoundObservationProcedure, SystemProperties systemProperties) {
        Concept followUpConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_FOLLOWUP);
        List<Obs> followupObses = compoundObservationProcedure.findAllMemberObsForConcept(followUpConcept);
        List<CodeableConceptDt> followUpCodeableConcepts = new ArrayList<>();
        for (Obs followupObs : followupObses) {
            followUpCodeableConcepts.add(codeableConceptService.getTRValueSetCodeableConcept(followupObs.getValueCoded(), TrValueSetType.PROCEDURE_FOLLOWUP.getTrPropertyValueSetUrl(systemProperties)));
        }
        return followUpCodeableConcepts.isEmpty() ? null : followUpCodeableConcepts;
    }


    private PeriodDt getProcedurePeriod(CompoundObservation compoundObservationProcedure) {
        Obs startDateObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_START_DATE);
        Obs endDateObs = compoundObservationProcedure.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_END_DATE);
        return getPeriod(startDateObs, endDateObs);
    }

    private PeriodDt getPeriod(Obs startDateObs, Obs endDateObs) {
        PeriodDt period = new PeriodDt();
        if (startDateObs != null) period.setStart(startDateObs.getValueDate(), TemporalPrecisionEnum.MILLI);
        if (startDateObs != null && endDateObs != null)
            period.setEnd(endDateObs.getValueDate(), TemporalPrecisionEnum.MILLI);
        return period;
    }

    private DiagnosticReport buildDiagnosticReport(CompoundObservation diagnosticStudyObs, Encounter fhirEncounter, SystemProperties systemProperties, List<FHIRResource> allResources) {
        CodeableConceptDt diagnosisTestName = getNameToDiagnosticReport(diagnosticStudyObs);
        if (diagnosisTestName != null) {
            DiagnosticReport diagnosticReport = diagnosticReportBuilder.build(diagnosticStudyObs.getRawObservation(), fhirEncounter, systemProperties);
            diagnosticReport.setCode(diagnosisTestName);
            addDiagnosticResults(diagnosticStudyObs, systemProperties, allResources, diagnosticReport);
            addDiagnosisToDiagnosticReport(diagnosticReport, diagnosticStudyObs);
            return diagnosticReport;
        }
        return null;
    }

    private void addDiagnosticResults(CompoundObservation diagnosticStudyObs, SystemProperties systemProperties, List<FHIRResource> allResources, DiagnosticReport diagnosticReport) {
        for (Obs diagnosticResultObs : diagnosticStudyObs.findAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT)) {
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
        List<Obs> diagnosisObses = compoundDiagnosticStudyObs.findAllMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSIS);
        for (Obs diagnosisObs : diagnosisObses) {
            CodeableConceptDt codeableType = codeableConceptService.addTRCodingOrDisplay(diagnosisObs.getValueCoded(), idMappingsRepository);
            if (codeableType != null && !codeableType.isEmpty()) {
                CodeableConceptDt codedDiagnosis = diagnosticReport.addCodedDiagnosis();
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
        String id = new EntityReference().build(IResource.class, systemProperties, diagnosticResultObs.getUuid());
        observation.addIdentifier().setValue(id);
        observation.setId(id);
        observation.setSubject(diagnosticReport.getSubject());
        observation.setEncounter(diagnosticReport.getEncounter());
        observation.setStatus(ObservationStatusEnum.FINAL);
        observation.setCode(diagnosticReport.getCode());
        observation.setValue(observationValueMapper.map(diagnosticResultObs));
        return observation;
    }

    private CodeableConceptDt getNameToDiagnosticReport(CompoundObservation compoundDiagnosticStudyObs) {
        CodeableConceptDt name = null;
        if (compoundDiagnosticStudyObs.getRawObservation() != null) {
            Obs diagnosticTestObs = compoundDiagnosticStudyObs.getMemberObsForConceptName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_TEST);
            name = diagnosticTestObs != null ? codeableConceptService.addTRCodingOrDisplay(diagnosticTestObs.getValueCoded(), idMappingsRepository) : null;
        }
        return name != null && name.isEmpty() ? null : name;
    }
}
