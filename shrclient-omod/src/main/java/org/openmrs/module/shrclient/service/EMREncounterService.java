package org.openmrs.module.shrclient.service;

import org.openmrs.Patient;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Transactional
public interface EMREncounterService {
    public void createOrUpdateEncounters(Patient emrPatient, List<EncounterEvent> encounterEvents) throws SQLException, Exception;

    public void createOrUpdateEncounter(Patient emrPatient, EncounterEvent encounterEvent) throws Exception;
}
