package org.openmrs.module.fhir.mapper.bundler;

import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.dstu3.model.Condition.ConditionClinicalStatus;
import org.hl7.fhir.dstu3.model.Condition.ConditionVerificationStatus;
import org.joda.time.DateTime;
import org.openmrs.Obs;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.CompoundObservation;
import org.openmrs.module.fhir.mapper.model.EntityReference;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.CodeableConceptService;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.openmrs.module.fhir.MRSProperties.TR_CONDITION_CATEGORY_COMPLAINT_CODE;
import static org.openmrs.module.fhir.MRSProperties.TR_CONDITION_CATEGORY_VALUESET_NAME;
import static org.openmrs.module.fhir.mapper.model.ObservationType.COMPLAINT_CONDITION_TEMPLATE;

@Component
public class ChiefComplaintMapper implements EmrObsResourceHandler {

    @Autowired
    private CodeableConceptService codeableConceptService;

    @Override
    public boolean canHandle(Obs observation) {
        CompoundObservation obs = new CompoundObservation(observation);
        return obs.isOfType(COMPLAINT_CONDITION_TEMPLATE);
    }

    @Override
    public List<FHIRResource> map(Obs obs, FHIREncounter fhirEncounter, SystemProperties systemProperties) {
        List<FHIRResource> chiefComplaints = new ArrayList<>();
        CompoundObservation conditionTemplateObs = new CompoundObservation(obs);
        List<Obs> chiefComplaintDataObsList = conditionTemplateObs.getAllMemberObsForConceptName(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DATA);
        for (Obs chiefComplaintDataObs : chiefComplaintDataObsList) {
            chiefComplaints.add(createFHIRCondition(fhirEncounter, chiefComplaintDataObs, systemProperties));
        }
        return chiefComplaints;
    }

    private FHIRResource createFHIRCondition(FHIREncounter fhirEncounter, Obs obs, SystemProperties systemProperties) {
        Condition condition = new Condition();
        condition.setContext(new Reference().setReference(fhirEncounter.getId()));
        condition.setSubject(fhirEncounter.getPatient());
        condition.setAsserter(fhirEncounter.getFirstParticipantReference());
        condition.setCategory(setComplainCategory(systemProperties));
        condition.setClinicalStatus(ConditionClinicalStatus.ACTIVE);
        condition.setVerificationStatus(ConditionVerificationStatus.PROVISIONAL);

        final Set<Obs> obsMembers = obs.getGroupMembers(false);
        for (Obs member : obsMembers) {
            final String memberConceptName = member.getConcept().getName().getName();
            if (memberConceptName.equalsIgnoreCase(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT)) {
                final CodeableConcept complaintCode = codeableConceptService.addTRCoding(member.getValueCoded());
                if (CollectionUtils.isEmpty(complaintCode.getCoding())) {
                    Coding coding = complaintCode.addCoding();
                    coding.setDisplay(member.getValueCoded().getName().getName());
                }
                condition.setCode(complaintCode);
            } else if (memberConceptName.equalsIgnoreCase(MRSProperties.MRS_CONCEPT_NAME_CHIEF_COMPLAINT_DURATION)) {
                condition.setOnset(getOnsetDate(member));
            } else if (memberConceptName.equalsIgnoreCase(MRSProperties.MRS_CONCEPT_NAME_NON_CODED_CHIEF_COMPLAINT)) {
                CodeableConcept nonCodedChiefComplaintCode = new CodeableConcept();
                Coding coding = nonCodedChiefComplaintCode.addCoding();
                coding.setDisplay(member.getValueText());
                condition.setCode(nonCodedChiefComplaintCode);
            }
        }

        Identifier identifier = condition.addIdentifier();
        String conditionId = new EntityReference().build(Obs.class, systemProperties, obs.getUuid());
        identifier.setValue(conditionId);
        condition.setId(conditionId);

        return new FHIRResource(MRSProperties.TR_CONDITION_CATEGORY_COMPLAINT_DISPLAY, condition.getIdentifier(), condition);
    }

    private List<CodeableConcept> setComplainCategory(SystemProperties systemProperties) {
        CodeableConcept codeableConcept = new CodeableConcept();
        String valuesetUrl = systemProperties.createValueSetUrlFor(TR_CONDITION_CATEGORY_VALUESET_NAME);
        codeableConcept.addCoding().setSystem(valuesetUrl).setCode(TR_CONDITION_CATEGORY_COMPLAINT_CODE);
        return asList(codeableConcept);
    }

    private Period getOnsetDate(Obs member) {
        Double durationInMinutes = member.getValueNumeric();
        final java.util.Date obsDatetime = member.getObsDatetime();
        org.joda.time.DateTime obsTime = new DateTime(obsDatetime);
        final java.util.Date assertedDateTime = obsTime.minusMinutes(durationInMinutes.intValue()).toDate();
        Period periodDt = new Period();
        periodDt.setStart(assertedDateTime, TemporalPrecisionEnum.MILLI);
        periodDt.setEnd(obsDatetime, TemporalPrecisionEnum.MILLI);
        return periodDt;
    }
}
