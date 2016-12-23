package org.openmrs.module.shrclient.service;

import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;
import org.openmrs.*;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class VisitLookupService {

    private VisitService visitService;

    @Autowired
    public VisitLookupService(VisitService visitService) {
        this.visitService = visitService;
    }

    public Visit findOrInitializeVisit(Patient patient, Date encounterDate, VisitType visitType, Location location, Date givenVisitStartDate, Date givenVisitStopDate) {
        Visit applicableVisit = getApplicableVisit(patient, encounterDate, visitType, location, givenVisitStartDate);
        if (applicableVisit != null) {
            return modifyVisitIfRequired(applicableVisit, givenVisitStopDate, encounterDate);
        }
        if (givenVisitStartDate == null) {
            givenVisitStartDate = encounterDate;
        }
        Visit visit = new Visit();
        visit.setPatient(patient);
        visit.setVisitType(visitType);
        visit.setStartDatetime(givenVisitStartDate);
        visit.setEncounters(new HashSet<Encounter>());
        visit.setUuid(UUID.randomUUID().toString());
        visit.setLocation(location);

        Visit nextVisit = getVisitForPatientForNearestStartDate(patient, givenVisitStartDate);
        DateTime startTime = new DateTime(givenVisitStartDate);
        if (nextVisit == null) {
            stopVisitWhenSuitable(visit, startTime, givenVisitStopDate);
        } else {
            stopVisitBeforeStartOfNextVisit(visit, nextVisit, startTime);
        }
        return visit;
    }

    private Visit getApplicableVisit(Patient patient, Date encounterDate, VisitType visitType, Location location, Date visitStartDate) {
        if (visitStartDate == null) {
            return getApplicableVisitForEncounterDate(visitType, patient, location, encounterDate);
        }
        return getApplicableVisitForVisitStartDate(visitType, patient, location, visitStartDate);
    }

    private Visit modifyVisitIfRequired(Visit applicableVisit, Date givenVisitStopDate, Date encounterDate) {
        if (givenVisitStopDate == null) {
            if (applicableVisit.getStopDatetime() == null || applicableVisit.getStopDatetime().before(encounterDate)) {
                Date today = new DateTime().toDate();
                if (DateUtils.isSameDay(encounterDate, today))
                    applicableVisit.setStopDatetime(DateUtil.aSecondBefore(today));
                else
                    applicableVisit.setStopDatetime(getEndOfDay(new DateTime(encounterDate)).toDate());
            }
        }
        return applicableVisit;
    }


    private void stopVisitBeforeStartOfNextVisit(Visit visit, Visit nextVisit, DateTime startTime) {
        DateTime nextVisitStartTime = new DateTime(nextVisit.getStartDatetime());
        DateTime visitStopDate = getEndOfDay(startTime);
        if (!visitStopDate.isBefore(nextVisitStartTime)) {
            visitStopDate = nextVisitStartTime.minusSeconds(1);
        }
        visit.setStopDatetime(visitStopDate.toDate());
    }

    private DateTime getEndOfDay(DateTime startTime) {
        return startTime.withTime(23, 59, 59, 000);
    }

    private void stopVisitWhenSuitable(Visit visit, DateTime startTime, Date stopTime) {
        if (stopTime == null) {
            stopTime = getEndOfDay(startTime).toDate();
            Date currentDateTime = new DateTime().toDate();
            if (DateUtils.isSameDay(startTime.toDate(), currentDateTime)) {
                stopTime = DateUtil.aSecondBefore(currentDateTime);
            }
        }
        visit.setStopDatetime(stopTime);
    }

    private Visit getVisitForPatientForNearestStartDate(Patient patient, Date startDate) {
        Date maxEndDate = getEndOfDay(new DateTime(startDate)).toDate();
        List<Visit> visits = visitService.getVisits(null, Arrays.asList(patient), null, null, startDate, null, null, maxEndDate, null, true, false);
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

    private Visit getApplicableVisitForEncounterDate(VisitType visitType, Patient patient, Location location, Date startTime) {
        List<Visit> visits = visitService.getVisits(Arrays.asList(visitType), Arrays.asList(patient),
                Arrays.asList(location), null, null, startTime, startTime, null, null, true, false);
        return visits.isEmpty() ? null : visits.get(0);
    }

    private Visit getApplicableVisitForVisitStartDate(VisitType visitType, Patient patient, Location location, Date visitStartDate) {
        List<Visit> visits = visitService.getVisits(Arrays.asList(visitType), Arrays.asList(patient),
                Arrays.asList(location), null, visitStartDate, visitStartDate, null, null, null, true, false);
        return visits.isEmpty() ? null : visits.get(0);
    }
}
