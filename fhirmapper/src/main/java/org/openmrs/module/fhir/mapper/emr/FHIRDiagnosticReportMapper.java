package org.openmrs.module.fhir.mapper.emr;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import org.apache.commons.collections4.CollectionUtils;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.*;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;


@Component
public class FHIRDiagnosticReportMapper implements FHIRResourceMapper {
    private ConceptService conceptService;
    private FHIRObservationsMapper fhirObservationsMapper;
    private FHIRDiagnosticReportRequestHelper fhirDiagnosticReportRequestHelper;
    private OMRSConceptLookup omrsConceptLookup;
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    public FHIRDiagnosticReportMapper(ConceptService conceptService, FHIRObservationsMapper fhirObservationsMapper, FHIRDiagnosticReportRequestHelper fhirDiagnosticReportRequestHelper, OMRSConceptLookup omrsConceptLookup, GlobalPropertyLookUpService globalPropertyLookUpService) {
        this.conceptService = conceptService;
        this.fhirObservationsMapper = fhirObservationsMapper;
        this.fhirDiagnosticReportRequestHelper = fhirDiagnosticReportRequestHelper;
        this.omrsConceptLookup = omrsConceptLookup;
        this.globalPropertyLookUpService = globalPropertyLookUpService;
    }

    @Override
    public boolean canHandle(IResource resource) {
        if (resource instanceof DiagnosticReport) {
            DiagnosticReport report = (DiagnosticReport) resource;
            if (!hasLabCategory(report))
                return true;
        }
        return false;
    }

    @Override
    public void map(IResource resource, EmrEncounter emrEncounter, ShrEncounterBundle shrEncounterBundle, SystemProperties systemProperties) {
        Obs fulfillmentObs = new Obs();
        DiagnosticReport report = (DiagnosticReport) resource;
        Concept topLevelObsConcept = conceptService.getConceptByName(getFulfillmentFormConceptName(report));
        if (null == topLevelObsConcept) {
            String facilityId = FHIREncounterUtil.getFacilityId(shrEncounterBundle.getBundle());
            ConceptClass miscClass = conceptService.getConceptClassByUuid(ConceptClass.MISC_UUID);
            ConceptDatatype textDataType = conceptService.getConceptDatatypeByUuid(ConceptDatatype.TEXT_UUID);
            topLevelObsConcept = omrsConceptLookup.createLocalConceptFromCodings(report.getCategory().getCoding(), facilityId, miscClass, textDataType);
        }
        fulfillmentObs.setConcept(topLevelObsConcept);
        addGroupMembers(report, fulfillmentObs, shrEncounterBundle, emrEncounter);

        Concept reportConcept = omrsConceptLookup.findConceptByCode(report.getCode().getCoding());
        Order order = fhirDiagnosticReportRequestHelper.getOrder(report, reportConcept);
        if (order != null) addOrderToResult(fulfillmentObs, order);

        emrEncounter.addObs(fulfillmentObs);
    }

    private String getFulfillmentFormConceptName(DiagnosticReport report) {
        for (CodingDt codingDt : report.getCategory().getCoding()) {
            List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
            for (OpenMRSOrderTypeMap configuredOrderType : configuredOrderTypes) {
                if (FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL.equals(codingDt.getSystem()) &&
                        configuredOrderType.getCode().equals(codingDt.getCode()))
                    return configuredOrderType.getType() + MRSProperties.MRS_ORDER_FULFILLMENT_FORM_SUFFIX;
            }
        }
        return null;
    }

    private void addGroupMembers(DiagnosticReport report, Obs fulfillmentObs, ShrEncounterBundle shrEncounterBundle, EmrEncounter emrEncounter) {
        List<ResourceReferenceDt> result = report.getResult();
        for (ResourceReferenceDt resultReference : result) {
            Observation observationByReference = (Observation) FHIRBundleHelper.findResourceByReference(shrEncounterBundle.getBundle(), resultReference);
            Obs resultObs = fhirObservationsMapper.mapObs(shrEncounterBundle, emrEncounter, observationByReference);
            fulfillmentObs.addGroupMember(resultObs);
        }
    }

    private void addOrderToResult(Obs obs, Order order) {
        obs.setOrder(order);
        Set<Obs> groupMembers = obs.getGroupMembers();
        if (CollectionUtils.isNotEmpty(groupMembers)) {
            for (Obs member : groupMembers) {
                addOrderToResult(member, order);
            }
        }
    }

    private boolean hasLabCategory(DiagnosticReport report) {
        if (report.getCategory().isEmpty()) return true;
        for (CodingDt codingDt : report.getCategory().getCoding()) {
            if (FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL.equals(codingDt.getSystem()) &&
                    FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE.equals(codingDt.getCode()))
                return true;
        }
        return false;
    }
}
