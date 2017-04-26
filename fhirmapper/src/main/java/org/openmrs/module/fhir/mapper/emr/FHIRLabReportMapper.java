package org.openmrs.module.fhir.mapper.emr;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.*;
import org.openmrs.*;
import org.openmrs.Location;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.fhir.utils.FHIRDiagnosticReportRequestHelper;
import org.openmrs.module.fhir.utils.OMRSConceptLookup;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openmrs.module.fhir.MRSProperties.MRS_CONCEPT_CLASS_LAB_SET;
import static org.openmrs.module.fhir.MRSProperties.MRS_CONCEPT_NAME_LAB_NOTES;
import static org.openmrs.module.fhir.utils.FHIRBundleHelper.findResourcesByReference;

@Component
public class FHIRLabReportMapper implements FHIRResourceMapper {
    private OMRSConceptLookup omrsConceptLookup;
    private FHIRObservationsMapper observationsMapper;
    private ConceptService conceptService;
    private FHIRDiagnosticReportRequestHelper fhirDiagnosticReportRequestHelper;

    @Autowired
    public FHIRLabReportMapper(OMRSConceptLookup omrsConceptLookup, FHIRObservationsMapper observationsMapper,
                               ConceptService conceptService, FHIRDiagnosticReportRequestHelper fhirDiagnosticReportRequestHelper) {
        this.omrsConceptLookup = omrsConceptLookup;
        this.observationsMapper = observationsMapper;
        this.conceptService = conceptService;
        this.fhirDiagnosticReportRequestHelper = fhirDiagnosticReportRequestHelper;
    }

    @Override
    public boolean canHandle(Resource resource) {
        if (resource instanceof DiagnosticReport) {
            DiagnosticReport report = (DiagnosticReport) resource;
            if (hasLabCategory(report))
                return true;
        }
        return false;
    }

    private boolean hasLabCategory(DiagnosticReport report) {
        if (report.getCategory().isEmpty()) return true;
        for (Coding coding : report.getCategory().getCoding()) {
            if (FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL.equals(coding.getSystem()) &&
                    FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE.equals(coding.getCode()))
                return true;
        }
        return false;
    }

    @Override
    public void map(Resource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        DiagnosticReport diagnosticReport = (DiagnosticReport) resource;
        Order order = null;
        Concept concept = omrsConceptLookup.findConceptByCode(diagnosticReport.getCode().getCoding());
        if (concept != null) {
            order = fhirDiagnosticReportRequestHelper.getOrder(diagnosticReport, concept);
        } else {
            final org.hl7.fhir.dstu3.model.Encounter shrEncounter = FHIRBundleHelper.getEncounter(shrEncounterBundle.getBundle());
            String facilityId = new EntityReference().parse(Location.class, shrEncounter.getServiceProvider().getReference());
            ConceptDatatype textDatatype = conceptService.getConceptDatatypeByUuid(ConceptDatatype.TEXT_UUID);
            ConceptClass labSetClass = conceptService.getConceptClassByName(MRSProperties.MRS_CONCEPT_CLASS_LAB_SET);
            concept = omrsConceptLookup.createLocalConceptFromCodings(diagnosticReport.getCode().getCoding(), facilityId, labSetClass, textDatatype);
        }

        Obs topLevelResultObsGroup = buildObs(concept, order);

        Set<Obs> resultObsGroups = buildResultObsGroup(shrEncounterBundle, emrEncounter, diagnosticReport, order, concept);

        for (Obs resultObs : resultObsGroups) {
            topLevelResultObsGroup.addGroupMember(resultObs);
        }

        if (order != null && order.getConcept().getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET)) {
            Obs panelObs = findObsByOrder(emrEncounter, order);
            if (panelObs == null) {
                panelObs = buildObs(order.getConcept(), order);
            }
            panelObs.addGroupMember(topLevelResultObsGroup);
            emrEncounter.addObs(panelObs);
        }
        emrEncounter.addObs(topLevelResultObsGroup);
    }

    private Obs getNotes(Observation observation, Order order) {
        String comments = observation.getComment();
        if (StringUtils.isNotBlank(comments)) {
            Concept labNotesConcept = conceptService.getConceptByName(MRS_CONCEPT_NAME_LAB_NOTES);
            Obs notesObs = buildObs(labNotesConcept, order);
            notesObs.setValueText(comments);
            return notesObs;
        }
        return null;
    }

    private Set<Obs> buildResultObsGroup(ShrEncounterBundle encounterComposition, EmrEncounter emrEncounter, DiagnosticReport diagnosticReport, Order order, Concept concept) {
        Set<Obs> resultObsGroups = new HashSet<>();
        List<Resource> resultObservationList = findResourcesByReference(encounterComposition.getBundle(), diagnosticReport.getResult());
        if (CollectionUtils.isEmpty(resultObservationList)) return Collections.emptySet();

        for (Resource resultObservation : resultObservationList) {
            Obs resultObsGroup = buildObs(concept, order);
            populateResultsAndNotes(encounterComposition, emrEncounter, order, (Observation) resultObservation, resultObsGroup);
            resultObsGroups.add(resultObsGroup);
        }
        return resultObsGroups;
    }

    private void populateResultsAndNotes(ShrEncounterBundle encounterComposition, EmrEncounter emrEncounter, Order order, Observation resultObservation, Obs resultObsGroup) {
        Obs resultObs = observationsMapper.mapObs(encounterComposition, emrEncounter, resultObservation);
        resultObs.setOrder(order);
        resultObsGroup.addGroupMember(resultObs);
        resultObsGroup.addGroupMember(getNotes(resultObservation, order));
    }

    private Obs findObsByOrder(EmrEncounter emrEncounter, Order order) {
        for (Obs obs : emrEncounter.getTopLevelObs()) {
            Order obsOrder = obs.getOrder();
            if (obsOrder != null && order.equals(obsOrder) && obs.getConcept().equals(order.getConcept())) {
                return obs;
            }
        }
        return null;
    }

    private Obs buildObs(Concept concept, Order order) {
        Obs obs = new Obs();
        obs.setConcept(concept);
        obs.setOrder(order);
        return obs;
    }
}
