package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.instance.model.*;
import org.openmrs.Obs;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.utils.FHIRFeedHelper;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.Boolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hl7.fhir.instance.model.DiagnosticReport.DiagnosticReportStatus;
import static org.openmrs.module.fhir.mapper.MRSProperties.*;

@Component("testResultMapper")
public class TestResultMapper implements EmrObsResourceHandler {

    @Autowired
    private ObservationMapper observationMapper;

    @Autowired
    private IdMappingsRepository idMappingsRepository;

    @Override
    public boolean handles(Obs observation) {
        return MRS_ENC_TYPE_LAB_RESULT.equals(observation.getEncounter().getEncounterType().getName());
    }

    @Override
    public List<EmrResource> map(Obs obs, Encounter fhirEncounter, SystemProperties systemProperties) {
        List<EmrResource> emrResourceList = new ArrayList<>();
        if (obs != null) {
            if (!isPanel(obs)) {
                buildTestResult(obs, fhirEncounter, emrResourceList, systemProperties);
            } else {
                for (Obs observation : obs.getGroupMembers()) {
                    buildTestResult(observation, fhirEncounter, emrResourceList, systemProperties);
                }
            }
        }
        return emrResourceList;
    }

    private Boolean isPanel(Obs obs) {
        return obs.getConcept().getConceptClass().getName().equals(MRS_CONCEPT_CLASS_LAB_SET);
    }

    private void buildTestResult(Obs obs, Encounter fhirEncounter, List<EmrResource> emrResourceList, SystemProperties systemProperties) {
        for (Obs observation : obs.getGroupMembers()) {
            DiagnosticReport diagnosticReport = build(observation, fhirEncounter, emrResourceList, systemProperties);
            if (diagnosticReport != null) {
                EmrResource emrResource = new EmrResource("Diagnostic Report", Arrays.asList(diagnosticReport.getIdentifier()), diagnosticReport);
                emrResourceList.add(emrResource);
            }
        }
    }

    private DiagnosticReport build(Obs obs, Encounter fhirEncounter, List<EmrResource> emrResourceList, SystemProperties systemProperties) {
        DiagnosticReport report = new DiagnosticReport();
        CodeableConcept name = FHIRFeedHelper.addReferenceCodes(obs.getConcept(), idMappingsRepository);
        if (name.getCoding().isEmpty()) {
            return null;
        }
        report.setName(name);
        report.setStatus(new Enumeration<DiagnosticReportStatus>(DiagnosticReportStatus.final_));
        report.setIssuedSimple(new DateAndTime(obs.getObsDatetime()));
        report.setSubject(fhirEncounter.getSubject());
        Identifier identifier = new Identifier();
        identifier.setValueSimple(new EntityReference().build(Obs.class, systemProperties, obs.getUuid()));
        report.setIdentifier(identifier);
        List<Encounter.EncounterParticipantComponent> participants = fhirEncounter.getParticipant();
        if (CollectionUtils.isNotEmpty(participants)) {
            report.setPerformer(participants.get(0).getIndividual());
        }
        DateTime diagnostic = new DateTime();
        org.openmrs.Order obsOrder = obs.getOrder();
        diagnostic.setValue(new DateAndTime(obsOrder.getDateActivated()));
        report.setDiagnostic(diagnostic);

        String uuid = obsOrder.getEncounter().getUuid();
        IdMapping encounterIdMapping = idMappingsRepository.findByInternalId(uuid);
        if (encounterIdMapping == null) {
            throw new RuntimeException("Encounter id [" + uuid + "] doesn't have id mapping.");
        }

        ResourceReference requestDetail = report.addRequestDetail();
        requestDetail.setReferenceSimple(encounterIdMapping.getUri());

        for (Obs member : obs.getGroupMembers()) {
            if (member.getConcept().equals(obs.getConcept())) {
                List<EmrResource> observationResources = observationMapper.map(member, fhirEncounter, systemProperties);
                ResourceReference resourceReference = report.addResult();
                // TODO: how do we identify this observation?
                resourceReference.setReferenceSimple(observationResources.get(0).getIdentifier().getValueSimple());
                emrResourceList.addAll(observationResources);
            } else if (MRS_CONCEPT_NAME_LAB_NOTES.equals(member.getConcept().getName().getName())) {
                report.setConclusionSimple(member.getValueText());
            }
        }
        return report;
    }

    //TODO : how do we identify this individual?
    protected ResourceReference getParticipant(Encounter encounter) {
        List<Encounter.EncounterParticipantComponent> participants = encounter.getParticipant();
        if ((participants != null) && !participants.isEmpty()) {
            return participants.get(0).getIndividual();
        }
        return null;
    }
}
