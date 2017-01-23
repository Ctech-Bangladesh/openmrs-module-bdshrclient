package org.openmrs.module.fhir.mapper.emr;


import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.AnnotationDt;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.PeriodDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Procedure;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.fhir.utils.TrValueSetType;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class FHIRProcedureMapper implements FHIRResourceMapper {

    @Autowired
    private ConceptService conceptService;

    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Autowired
    private FHIRObservationValueMapper observationValueMapper;

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Autowired
    private OrderService orderService;

    @Override
    public boolean canHandle(IResource resource) {
        return resource instanceof Procedure;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Procedure procedure = (Procedure) resource;

        Obs proceduresObs = new Obs();
        proceduresObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURES_TEMPLATE));

        Order procedureOrder = getProcedureOrder(procedure);
        final ca.uhn.fhir.model.dstu2.resource.Encounter shrEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        String facilityId = new EntityReference().parse(Location.class, shrEncounter.getServiceProvider().getReference().getValue());
        Obs procedureType = getProcedureType(procedure, procedureOrder, facilityId);
        if (procedureType == null) return;
        if (shouldFailDownload(procedure, procedureOrder, procedureType)) {
            String requestReference = procedure.getRequest().getReference().getValue();
            throw new RuntimeException(String.format("The procedure order with SHR reference [%s] is not yet synced", requestReference));
        }
        proceduresObs.setOrder(procedureOrder);
        proceduresObs.addGroupMember(procedureType);
        proceduresObs.addGroupMember(getStartDate(procedure, procedureOrder));
        proceduresObs.addGroupMember(getEndDate(procedure, procedureOrder));
        proceduresObs.addGroupMember(getOutCome(procedure, procedureOrder));
        setFollowUpObses(procedure, proceduresObs, procedureOrder);
        getProcedureNotesObs(procedure, proceduresObs, procedureOrder);
        proceduresObs.addGroupMember(getProcedureStatusObs(procedure, procedureOrder));

        for (ResourceReferenceDt reportReference : procedure.getReport()) {
            IResource diagnosticReportResource = FHIRBundleHelper.findResourceByReference(shrEncounterBundle.getBundle(), reportReference);
            if (diagnosticReportResource != null && diagnosticReportResource instanceof DiagnosticReport) {
                proceduresObs.addGroupMember(getDiagnosisStudyObs((DiagnosticReport) diagnosticReportResource, shrEncounterBundle.getBundle(), procedureOrder));
            }
        }
        if (procedureOrder == null) {
            emrEncounter.addObs(proceduresObs);
            return;
        }
        Obs fulfillmentObs = new Obs();
        fulfillmentObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_ORDER_FULFILLMENT_FORM));
        fulfillmentObs.addGroupMember(proceduresObs);
        fulfillmentObs.setOrder(procedureOrder);
        emrEncounter.addObs(fulfillmentObs);
    }

    private boolean shouldFailDownload(Procedure procedure, Order procedureOrder, Obs procedureType) {
        if (procedure.getRequest().isEmpty()) return false;
        if (isLocallyCreatedConcept(procedureType.getValueCoded())) return false;
        return procedureOrder == null;
    }

    private boolean isLocallyCreatedConcept(Concept concept) {
        return concept.getVersion() != null && concept.getVersion().startsWith(LOCAL_CONCEPT_VERSION_PREFIX);
    }

    private Order getProcedureOrder(Procedure procedure) {
        if (procedure.getRequest().isEmpty()) return null;
        String procedureRequestUrl = procedure.getRequest().getReference().getValue();
        String requestEncounterId = new EntityReference().parse(Encounter.class, procedureRequestUrl);
        String procedureRequestReference = StringUtils.substringAfterLast(procedureRequestUrl, "/");
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, requestEncounterId, procedureRequestReference);
        IdMapping mapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_ORDER);
        if (mapping != null) {
            return orderService.getOrderByUuid(mapping.getInternalId());
        }
        return null;
    }

    private Obs getProcedureStatusObs(Procedure procedure, Order procedureOrder) {
        Obs statusObs = new Obs();
        statusObs.setConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_STATUS));
        Concept statusConcept = omrsConceptLookup.findValuesetConceptFromTrValuesetType(TrValueSetType.PROCEDURE_STATUS, procedure.getStatus());
        statusObs.setValueCoded(statusConcept);
        statusObs.setOrder(procedureOrder);
        return statusObs;
    }

    private void getProcedureNotesObs(Procedure procedure, Obs proceduresObs, Order procedureOrder) {
        for (AnnotationDt annotationDt : procedure.getNotes()) {
            Obs procedureNotesObs = new Obs();
            procedureNotesObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_NOTES));
            procedureNotesObs.setValueText(annotationDt.getText());
            procedureNotesObs.setOrder(procedureOrder);
            proceduresObs.addGroupMember(procedureNotesObs);
        }
    }

    private Obs getDiagnosisStudyObs(DiagnosticReport diagnosticReport, Bundle bundle, Order procedureOrder) {
        Obs diagnosisStudyObs = new Obs();
        diagnosisStudyObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_STUDY));

        Obs diagnosticTest = mapObservationForConcept(diagnosticReport.getCode(), MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_TEST);
        if (diagnosticTest != null) {
            diagnosticTest.setOrder(procedureOrder);
            diagnosisStudyObs.addGroupMember(diagnosticTest);
        }

        addDiagnosticResults(diagnosticReport, bundle, diagnosisStudyObs, procedureOrder);
        addCodedDiagnoses(diagnosticReport, diagnosisStudyObs, procedureOrder);
        diagnosisStudyObs.setOrder(procedureOrder);
        return diagnosisStudyObs;
    }

    private void addCodedDiagnoses(DiagnosticReport diagnosticReport, Obs diagnosisStudyObs, Order procedureOrder) {
        for (CodeableConceptDt diagnosis : diagnosticReport.getCodedDiagnosis()) {
            Obs diagnosisObs = mapObservationForConcept(diagnosis, MRS_CONCEPT_PROCEDURE_DIAGNOSIS);
            if (diagnosisObs != null) {
                diagnosisObs.setOrder(procedureOrder);
                diagnosisStudyObs.addGroupMember(diagnosisObs);
            }
        }
    }

    private void addDiagnosticResults(DiagnosticReport diagnosticReport, Bundle bundle, Obs diagnosisStudyObs, Order procedureOrder) {
        Concept diagnosticResultConcept = conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT);
        for (ResourceReferenceDt resultReference : diagnosticReport.getResult()) {
            Obs result = new Obs();
            result.setConcept(diagnosticResultConcept);
            Observation resultObservation = (Observation) FHIRBundleHelper.findResourceByReference(bundle, resultReference);
            observationValueMapper.map(resultObservation.getValue(), result);
            result.setOrder(procedureOrder);
            diagnosisStudyObs.addGroupMember(result);
        }
    }

    private Obs getProcedureType(Procedure procedure, Order procedureOrder, String facilityId) {
        CodeableConceptDt procedureType = procedure.getCode();
        Concept concept = conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_TYPE);
        Concept answerConcept = omrsConceptLookup.findOrCreateLocalConceptByCodings(procedureType.getCoding(), facilityId, ConceptClass.PROCEDURE_UUID, ConceptDatatype.N_A_UUID);
        if (concept != null && answerConcept != null) {
            Obs obs = new Obs();
            obs.setConcept(concept);
            obs.setValueCoded(answerConcept);
            obs.setOrder(procedureOrder);
            return obs;
        }
        return null;
    }

    private Obs getStartDate(Procedure procedure, Order procedureOrder) {
        PeriodDt period = (PeriodDt) procedure.getPerformed();
        Obs startDate = null;
        if (period != null) {
            startDate = new Obs();
            startDate.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_START_DATE));
            startDate.setValueDate(period.getStart());
            startDate.setOrder(procedureOrder);
        }
        return startDate;
    }

    private Obs getEndDate(Procedure procedure, Order procedureOrder) {
        PeriodDt period = (PeriodDt) procedure.getPerformed();
        Obs endDate = null;
        if (period != null) {
            endDate = new Obs();
            endDate.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_END_DATE));
            endDate.setValueDate(period.getEnd());
            endDate.setOrder(procedureOrder);
        }
        return endDate;
    }

    private Obs getOutCome(Procedure procedure, Order procedureOrder) {
        if (procedure.getOutcome() != null && !procedure.getOutcome().isEmpty()) {
            Obs outcome = new Obs();
            outcome.setConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_OUTCOME));
            outcome.setValueCoded(omrsConceptLookup.findConceptByCodeOrDisplay(procedure.getOutcome().getCoding()));
            outcome.setOrder(procedureOrder);
            return outcome;
        }
        return null;
    }

    private void setFollowUpObses(Procedure procedure, Obs procedureObs, Order procedureOrder) {
        for (CodeableConceptDt followUp : procedure.getFollowUp()) {
            Obs followUpObs = mapObservationForConcept(followUp, MRS_CONCEPT_PROCEDURE_FOLLOWUP);
            if (followUpObs != null) {
                followUpObs.setOrder(procedureOrder);
                procedureObs.addGroupMember(followUpObs);
            }
        }
    }

    private Obs mapObservationForConcept(CodeableConceptDt codeableConcept, String conceptName) {
        Concept concept = conceptService.getConceptByName(conceptName);
        Concept answerConcept = omrsConceptLookup.findConceptByCodeOrDisplay(codeableConcept.getCoding());
        if (concept != null && answerConcept != null) {
            Obs obs = new Obs();
            obs.setConcept(concept);
            obs.setValueCoded(answerConcept);
            return obs;
        }
        return null;
    }
}
