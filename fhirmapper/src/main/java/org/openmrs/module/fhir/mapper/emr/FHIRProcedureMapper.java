package org.openmrs.module.fhir.mapper.emr;


import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.*;
import org.openmrs.Encounter;
import org.openmrs.Location;
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
import static org.openmrs.module.fhir.OpenMRSConstants.NA_CONCEPT_DATATYPE_NAME;
import static org.openmrs.module.fhir.OpenMRSConstants.PROCEDURE_CONCEPT_CLASS_NAME;

@Component
public class FHIRProcedureMapper implements FHIRResourceMapper {

    private final ConceptService conceptService;
    private final OMRSConceptLookup omrsConceptLookup;
    private final FHIRObservationValueMapper observationValueMapper;
    private final IdMappingRepository idMappingRepository;
    private final OrderService orderService;

    @Autowired
    public FHIRProcedureMapper(ConceptService conceptService, OMRSConceptLookup omrsConceptLookup,
                               FHIRObservationValueMapper observationValueMapper,
                               IdMappingRepository idMappingRepository, OrderService orderService) {
        this.conceptService = conceptService;
        this.omrsConceptLookup = omrsConceptLookup;
        this.observationValueMapper = observationValueMapper;
        this.idMappingRepository = idMappingRepository;
        this.orderService = orderService;
    }

    @Override
    public boolean canHandle(Resource resource) {
        return resource instanceof Procedure;
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Procedure procedure = (Procedure) resource;

        Obs proceduresObs = new Obs();
        proceduresObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURES_TEMPLATE));

        Order procedureOrder = getProcedureOrder(procedure);
        final org.hl7.fhir.dstu3.model.Encounter shrEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
        String facilityId = new EntityReference().parse(Location.class, shrEncounter.getServiceProvider().getReference());
        Obs procedureType = getProcedureType(procedure, procedureOrder, facilityId);
        if (procedureType == null) return;
        if (shouldFailDownload(procedure, procedureOrder, procedureType)) {
            String requestReference = procedure.getBasedOn().get(0).getReference();
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

        for (Reference reportReference : procedure.getReport()) {
            Resource diagnosticReportResource = FHIRBundleHelper.findResourceByReference(shrEncounterBundle.getBundle(), reportReference);
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
        if (procedure.getBasedOn().isEmpty()) return false;
        if (isLocallyCreatedConcept(procedureType.getValueCoded())) return false;
        return procedureOrder == null;
    }

    private boolean isLocallyCreatedConcept(Concept concept) {
        return concept.getVersion() != null && concept.getVersion().startsWith(LOCAL_CONCEPT_VERSION_PREFIX);
    }

    private Order getProcedureOrder(Procedure procedure) {
        if (procedure.getBasedOn().isEmpty()) return null;
        String procedureRequestUrl = procedure.getBasedOn().get(0).getReference();
        String requestEncounterId = new EntityReference().parse(Encounter.class, procedureRequestUrl);
        String procedureRequestReference = StringUtils.substringAfterLast(procedureRequestUrl, "/");
        String externalId = String.format(RESOURCE_MAPPING_EXTERNAL_ID_FORMAT, requestEncounterId, procedureRequestReference);
        IdMapping mapping = idMappingRepository.findByExternalId(externalId, IdMappingType.PROCEDURE_REQUEST);
        if (mapping != null) {
            return orderService.getOrderByUuid(mapping.getInternalId());
        }
        return null;
    }

    private Obs getProcedureStatusObs(Procedure procedure, Order procedureOrder) {
        Obs statusObs = new Obs();
        statusObs.setConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_STATUS));
        Concept statusConcept = omrsConceptLookup.findValuesetConceptFromTrValuesetType(TrValueSetType.PROCEDURE_STATUS, procedure.getStatus().toCode());
        statusObs.setValueCoded(statusConcept);
        statusObs.setOrder(procedureOrder);
        return statusObs;
    }

    private void getProcedureNotesObs(Procedure procedure, Obs proceduresObs, Order procedureOrder) {
        for (Annotation annotation : procedure.getNote()) {
            if (annotation.getText() == null) continue;
            Obs procedureNotesObs = new Obs();
            procedureNotesObs.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_NOTES));
            procedureNotesObs.setValueText(annotation.getText());
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
        for (CodeableConcept diagnosis : diagnosticReport.getCodedDiagnosis()) {
            Obs diagnosisObs = mapObservationForConcept(diagnosis, MRS_CONCEPT_PROCEDURE_DIAGNOSIS);
            if (diagnosisObs != null) {
                diagnosisObs.setOrder(procedureOrder);
                diagnosisStudyObs.addGroupMember(diagnosisObs);
            }
        }
    }

    private void addDiagnosticResults(DiagnosticReport diagnosticReport, Bundle bundle, Obs diagnosisStudyObs, Order procedureOrder) {
        Concept diagnosticResultConcept = conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_DIAGNOSTIC_RESULT);
        for (Reference resultReference : diagnosticReport.getResult()) {
            Observation resultObservation = (Observation) FHIRBundleHelper.findResourceByReference(bundle, resultReference);
            if (resultObservation == null || resultObservation.getValue() == null) continue;
            Obs result = new Obs();
            result.setConcept(diagnosticResultConcept);
            observationValueMapper.map(resultObservation.getValue(), result);
            result.setOrder(procedureOrder);
            diagnosisStudyObs.addGroupMember(result);
        }
    }

    private Obs getProcedureType(Procedure procedure, Order procedureOrder, String facilityId) {
        CodeableConcept procedureType = procedure.getCode();
        Concept concept = conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_TYPE);
        Concept answerConcept = omrsConceptLookup.findOrCreateLocalConceptByCodings(procedureType.getCoding(), facilityId, PROCEDURE_CONCEPT_CLASS_NAME, NA_CONCEPT_DATATYPE_NAME);
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
        Period period = (Period) procedure.getPerformed();
        Obs startDate = null;
        if (period != null && period.getStart() != null) {
            startDate = new Obs();
            startDate.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_START_DATE));
            startDate.setValueDate(period.getStart());
            startDate.setOrder(procedureOrder);
        }
        return startDate;
    }

    private Obs getEndDate(Procedure procedure, Order procedureOrder) {
        Period period = (Period) procedure.getPerformed();
        Obs endDate = null;
        if (period != null && period.getEnd() != null) {
            endDate = new Obs();
            endDate.setConcept(conceptService.getConceptByName(MRS_CONCEPT_PROCEDURE_END_DATE));
            endDate.setValueDate(period.getEnd());
            endDate.setOrder(procedureOrder);
        }
        return endDate;
    }

    private Obs getOutCome(Procedure procedure, Order procedureOrder) {
        if (procedure.getOutcome() == null || procedure.getOutcome().isEmpty()) return null;
        Concept outcomeAnswerConcept = omrsConceptLookup.findConceptByCodeOrDisplay(procedure.getOutcome().getCoding());
        if (outcomeAnswerConcept == null) return null;
        Obs outcome = new Obs();
        outcome.setConcept(omrsConceptLookup.findTRConceptOfType(TrValueSetType.PROCEDURE_OUTCOME));
        outcome.setValueCoded(outcomeAnswerConcept);
        outcome.setOrder(procedureOrder);
        return outcome;
    }

    private void setFollowUpObses(Procedure procedure, Obs procedureObs, Order procedureOrder) {
        for (CodeableConcept followUp : procedure.getFollowUp()) {
            Obs followUpObs = mapObservationForConcept(followUp, MRS_CONCEPT_PROCEDURE_FOLLOWUP);
            if (followUpObs != null) {
                followUpObs.setOrder(procedureOrder);
                procedureObs.addGroupMember(followUpObs);
            }
        }
    }

    private Obs mapObservationForConcept(CodeableConcept codeableConcept, String conceptName) {
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
