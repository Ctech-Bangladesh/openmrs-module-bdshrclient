package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.mapper.model.OpenMRSOrderTypeMap;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("radiologyFulfillmentMapper")
public class GenericOrderFulfillmentMapper implements EmrObsResourceHandler {

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Autowired
    private DiagnosticReportBuilder diagnosticReportBuilder;

    @Autowired
    private CodeableConceptService codeableConceptService;

    @Autowired
    private ObservationMapper observationMapper;

    @Autowired
    private ObservationBuilder observationBuilder;

    @Autowired
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Override
    public boolean canHandle(Obs observation) {
        String obsName = observation.getConcept().getName().getName();
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
            String orderFulfillmentForm = openMRSOrderTypeMap.getType() + MRSProperties.MRS_ORDER_FULFILLMENT_FORM_SUFFIX;
            if (obsName.equals(orderFulfillmentForm))
                return true;
        }
        return false;
    }

    @Override
    public List<FHIRResource> map(Obs orderFullfillmentObs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> fhirResources = new ArrayList<>();
        buildResult(orderFullfillmentObs, fhirEncounter, fhirResources, systemProperties);
        return fhirResources;
    }

    private void buildResult(Obs orderFullfillmentObs, FHIREncounter fhirEncounter, List<FHIRResource> fhirResourceList, SystemProperties systemProperties) {
        DiagnosticReport diagnosticReport = buildDiagnosticReport(orderFullfillmentObs, fhirEncounter, systemProperties);
        if (diagnosticReport != null) {
            for (Obs resultObs : orderFullfillmentObs.getGroupMembers()) {
                FHIRResource resultResource = getResultResource(resultObs, fhirEncounter, fhirResourceList, systemProperties);
                if (resultResource == null) continue;
                Reference resourceReference = diagnosticReport.addResult();
                resourceReference.setReference(resultResource.getIdentifier().getValue());
            }
            FHIRResource fhirResource = new FHIRResource("Diagnostic Report", diagnosticReport.getIdentifier(), diagnosticReport);
            fhirResourceList.add(fhirResource);
        }
    }

    private FHIRResource getResultResource(Obs resultObs, FHIREncounter fhirEncounter, List<FHIRResource> fhirResourceList, SystemProperties systemProperties) {
        return observationMapper.mapObs(resultObs, fhirEncounter, null, fhirResourceList, systemProperties);
    }

    private DiagnosticReport buildDiagnosticReport(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        DiagnosticReport report = diagnosticReportBuilder.build(obs, fhirEncounter, systemProperties);
        report.setCode(codeableConceptService.addTRCodingOrDisplay(obs.getOrder().getConcept()));
        org.openmrs.Order obsOrder = obs.getOrder();
        report.setEffective(getOrderTime(obsOrder));
        report.addBasedOn().setReference(getRequestUrl(obsOrder));
        report.setCategory(getCategory(obs));
        return report;
    }

    private CodeableConcept getCategory(Obs observation) {
        CodeableConcept category = new CodeableConcept();
        Coding codingDt = category.addCoding().setSystem(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL);
        String obsName = observation.getConcept().getName().getName();
        List<OpenMRSOrderTypeMap> configuredOrderTypes = globalPropertyLookUpService.getConfiguredOrderTypes();
        for (OpenMRSOrderTypeMap openMRSOrderTypeMap : configuredOrderTypes) {
            String orderFulfillmentForm = openMRSOrderTypeMap.getType() + MRSProperties.MRS_ORDER_FULFILLMENT_FORM_SUFFIX;
            if (obsName.equals(orderFulfillmentForm))
                codingDt.setCode(openMRSOrderTypeMap.getCode())
                        .setDisplay(openMRSOrderTypeMap.getDisplay());
        }
        return category;
    }

    private String getRequestUrl(Order order) {
        IdMapping orderIdMapping = idMappingRepository.findByInternalId(order.getUuid(), IdMappingType.PROCEDURE_REQUEST);
        if (orderIdMapping != null) {
            return orderIdMapping.getUri();
        }
        IdMapping encounterIdMapping = idMappingRepository.findByInternalId(order.getEncounter().getUuid(), IdMappingType.ENCOUNTER);
        if (encounterIdMapping != null)
            return encounterIdMapping.getUri();
        throw new RuntimeException("Encounter id [" + order.getEncounter().getUuid() + "] is not synced to SHR yet.");
    }

    private DateTimeType getOrderTime(org.openmrs.Order obsOrder) {
        DateTimeType diagnostic = new DateTimeType();
        diagnostic.setValue(obsOrder.getDateActivated(), TemporalPrecisionEnum.MILLI);
        return diagnostic;
    }
}
