package org.openmrs.module.shrclient.service;

import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.serialization.SerializationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EMRPatientService {
    public org.openmrs.Patient createOrUpdateEmrPatient(Patient mciPatient);

    public org.openmrs.Patient getEMRPatientByHealthId(String healthId);

    public void savePatient(org.openmrs.Patient emrPatient);

    public void mergePatients(org.openmrs.Patient toBeRetainedPatient, org.openmrs.Patient toBeRetiredPatient) throws SerializationException;
}

