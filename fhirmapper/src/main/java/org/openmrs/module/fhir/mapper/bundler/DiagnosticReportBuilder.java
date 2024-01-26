package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.stereotype.Component;

import static java.util.Arrays.asList;

@Component("diagnosticReportBuilder")
public class DiagnosticReportBuilder {
    public DiagnosticReport build(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        DiagnosticReport report = new DiagnosticReport();
        report.setContext(new Reference().setReference(fhirEncounter.getId()));
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        report.setIssued(obs.getObsDatetime());
        report.setSubject(fhirEncounter.getPatient());
        String reportId = new EntityReference().build(Obs.class, systemProperties, obs.getUuid());
        report.addIdentifier(new Identifier().setValue(reportId));
        report.setId(reportId);
        DiagnosticReport.DiagnosticReportPerformerComponent diagnosticReportPerformerComponent = new DiagnosticReport.DiagnosticReportPerformerComponent();
        diagnosticReportPerformerComponent.setActor(fhirEncounter.getFirstParticipantReference());
        //might have to set role
        report.setPerformer(asList(diagnosticReportPerformerComponent));

        DateTimeType diagnostic = new DateTimeType();
        diagnostic.setValue(obs.getDateCreated(), TemporalPrecisionEnum.MILLI);
        report.setEffective(diagnostic);
        return report;
    }
}
