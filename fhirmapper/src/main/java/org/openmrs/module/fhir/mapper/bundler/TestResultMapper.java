package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
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
import java.util.List;
import java.util.UUID;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component("testResultMapper")
public class TestResultMapper implements EmrObsResourceHandler {

    @Autowired
    private IdMappingRepository idMappingRepository;

    @Autowired
    private DiagnosticReportBuilder diagnosticReportBuilder;

    @Autowired
    private CodeableConceptService codeableConceptService;

    @Autowired
    private ObservationValueMapper observationValueMapper;

    @Autowired
    private ObservationBuilder observationBuilder;

    @Override
    public boolean canHandle(Obs observation) {
        return MRS_ENC_TYPE_LAB_RESULT.equals(observation.getEncounter().getEncounterType().getName());
    }

    @Override
    public List<FHIRResource> map(Obs topLevelObs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> FHIRResourceList = new ArrayList<>();
        if (topLevelObs != null) {
            if (!isPanel(topLevelObs)) {
                buildTestResult(topLevelObs, fhirEncounter, FHIRResourceList, systemProperties);
            } else {
                for (Obs testObsGroup : topLevelObs.getGroupMembers()) {
                    buildTestResult(testObsGroup, fhirEncounter, FHIRResourceList, systemProperties);
                }
            }
        }
        return FHIRResourceList;
    }

    private Boolean isPanel(Obs obs) {
        return obs.getConcept().getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET);
    }

    private void buildTestResult(Obs topLevelTestObs, FHIREncounter fhirEncounter, List<FHIRResource> fhirResourceList, SystemProperties systemProperties) {
        DiagnosticReport diagnosticReport = buildDiagnosticReport(topLevelTestObs, fhirEncounter, systemProperties);
        if (diagnosticReport != null) {
            for (Obs resultObsGroup : topLevelTestObs.getGroupMembers()) {
                FHIRResource resultResource = getResultResource(resultObsGroup, fhirEncounter, systemProperties);
                if (resultResource == null) return;
                Reference resourceReference = diagnosticReport.addResult();
                resourceReference.setReference(resultResource.getIdentifier().getValue());
                fhirResourceList.add(resultResource);

            }
            FHIRResource FHIRResource = new FHIRResource("Diagnostic Report", diagnosticReport.getIdentifier(), diagnosticReport);
            fhirResourceList.add(FHIRResource);
        }
    }

    private FHIRResource getResultResource(Obs resultObsGroup, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        CompoundObservation resultGroupObservation = new CompoundObservation(resultObsGroup);
        FHIRResource observationResource = getResultObservation(resultObsGroup, fhirEncounter, systemProperties, resultGroupObservation);

        Observation fhirObservation = (Observation) observationResource.getResource();
        setObservationStatusForResult(fhirObservation);
        setObservationNotes(resultGroupObservation, fhirObservation);

        return observationResource;
    }

    private void setObservationNotes(CompoundObservation resultGroupObservation, Observation fhirObservation) {
        Obs labNotesObs = resultGroupObservation.getMemberObsForConceptName(MRS_CONCEPT_NAME_LAB_NOTES);
        if (null != labNotesObs && null != labNotesObs.getValueText())
            fhirObservation.setCommentElement(new StringType(labNotesObs.getValueText()));
    }

    private void setObservationStatusForResult(Observation resultObservation) {
        resultObservation.setStatus(Observation.ObservationStatus.FINAL);
    }

    private FHIRResource getResultObservation(Obs resultObsGroup, FHIREncounter fhirEncounter, SystemProperties systemProperties, CompoundObservation resultGroupObservation) {
        Obs resultObs = resultGroupObservation.getMemberObsForConcept(resultObsGroup.getConcept());
        FHIRResource fhirObservationResource = observationBuilder.buildObservationResource(fhirEncounter,
                UUID.randomUUID().toString(), resultObsGroup.getConcept().getName().getName(), systemProperties);
        Observation fhirObservation = (Observation) fhirObservationResource.getResource();
        fhirObservation.setCode(codeableConceptService.addTRCodingOrDisplay(resultObsGroup.getConcept()));
        if (resultObs != null) {
            mapResultValue(resultObs, fhirObservation);
        }
        return fhirObservationResource;
    }

    private void mapResultValue(Obs resultObs, Observation fhirObservation) {
        Type value = observationValueMapper.map(resultObs);
        if (null != value) {
            fhirObservation.setValue(value);
        }
    }

    private DiagnosticReport buildDiagnosticReport(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        DiagnosticReport report = diagnosticReportBuilder.build(obs, fhirEncounter, systemProperties);
        CodeableConcept name = codeableConceptService.addTRCodingOrDisplay(obs.getConcept());
        report.setCode(name);
        org.openmrs.Order obsOrder = obs.getOrder();
        report.setEffective(getOrderTime(obsOrder));

        String uri = getRequestUrl(obsOrder);
        report.addBasedOn().setReference(uri);
        CodeableConcept category = new CodeableConcept();
        category.addCoding()
                .setSystem(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL)
                .setCode(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_CODE)
                .setDisplay(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_LAB_DISPLAY);
        report.setCategory(category);
        return report;
    }

    private String getRequestUrl(Order order) {
        IdMapping orderIdMapping = idMappingRepository.findByInternalId(order.getUuid(), IdMappingType.DIAGNOSTIC_ORDER);
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
        diagnostic.setValue(obsOrder.getDateActivated());
        return diagnostic;
    }
}
