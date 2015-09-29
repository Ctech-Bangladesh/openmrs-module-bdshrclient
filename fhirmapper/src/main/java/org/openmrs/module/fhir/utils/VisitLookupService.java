package org.openmrs.module.fhir.utils;

import ca.uhn.fhir.model.dstu2.valueset.EncounterClassEnum;
import org.apache.commons.lang.time.DateUtils;
import org.joda.time.DateTime;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.VisitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_IN_PATIENT_VISIT_TYPE;
import static org.openmrs.module.fhir.mapper.MRSProperties.MRS_OUT_PATIENT_VISIT_TYPE;

@Component
public class VisitLookupService {

    private VisitService visitService;

    @Autowired
    public VisitLookupService(VisitService visitService) {
        this.visitService = visitService;
    }

    public Visit findOrInitializeVisit(Patient patient, Date visitDate, String fhirEncounterClass) {
        Visit applicableVisit = getVisitForPatientWithinDates(patient, visitDate);
        if (applicableVisit != null) {
            return applicableVisit;
        }
        Visit visit = new Visit();
        visit.setPatient(patient);
        visit.setVisitType(getVisitType(fhirEncounterClass));
        visit.setStartDatetime(visitDate);
        visit.setEncounters(new HashSet<Encounter>());
        visit.setUuid(UUID.randomUUID().toString());

        Visit nextVisit = getVisitForPatientForNearestStartDate(patient, visitDate);
        DateTime startTime = new DateTime(visitDate);
        if (nextVisit == null) {
            if (!DateUtils.isSameDay(visitDate, new Date())) {
                Date stopTime = startTime.withTime(23, 59, 59, 000).toDate();
                visit.setStopDatetime(stopTime);
            }
        } else {
            DateTime nextVisitStartTime = new DateTime(nextVisit.getStartDatetime());
            DateTime visitStopDate = startTime.withTime(23, 59, 59, 000);
            boolean isEndTimeBeforeNextVisitStart = visitStopDate.isBefore(nextVisitStartTime);
            if (!isEndTimeBeforeNextVisitStart) {
                visitStopDate = nextVisitStartTime.minusSeconds(1);
            }
            visit.setStopDatetime(visitStopDate.toDate());
        }
        return visit;
    }

    private VisitType identifyVisitTypeByName(List<VisitType> allVisitTypes, String visitTypeName) {
        VisitType encVisitType = null;
        for (VisitType visitType : allVisitTypes) {
            if (visitType.getName().equalsIgnoreCase(visitTypeName)) {
                encVisitType = visitType;
                break;
            }
        }
        return encVisitType;
    }

    private Visit getVisitForPatientForNearestStartDate(Patient patient, Date startTime) {
        List<Visit> visits = visitService.getVisits(null, Arrays.asList(patient), null, null, startTime, null, null, null, null, true, false);
        if (visits.isEmpty()) {
            return null;
        }
        Collections.sort(visits, new Comparator<Visit>() {
            @Override
            public int compare(Visit v1, Visit v2) {
                return v1.getStartDatetime().compareTo(v2.getStartDatetime());
            }
        });
        return visits.get(0);
    }

    public VisitType getVisitType(String encounterClass) {
        List<VisitType> allVisitTypes = visitService.getAllVisitTypes();
        VisitType encVisitType = identifyVisitTypeByName(allVisitTypes, encounterClass);
        if (encVisitType != null) {
            return encVisitType;
        }

        if (encounterClass.equals(EncounterClassEnum.INPATIENT.getCode())) {
            return identifyVisitTypeByName(allVisitTypes, MRS_IN_PATIENT_VISIT_TYPE);
        } else {
            return identifyVisitTypeByName(allVisitTypes, MRS_OUT_PATIENT_VISIT_TYPE);
        }
    }

    private Visit getVisitForPatientWithinDates(Patient patient, Date startTime) {
        List<Visit> visits = visitService.getVisits(null, Arrays.asList(patient), null, null, null, startTime, startTime, null, null, true, false);
        return visits.isEmpty() ? null : visits.get(0);
    }
}
