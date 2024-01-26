package org.openmrs.module.shrclient.service;

import org.openmrs.serialization.SerializationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface EMRPatientMergeService {
    public void mergePatients(String toBeRetainedHealthId, String toBeRetiredHealthId) throws SerializationException;
    public List<String> mergePatients(String toBeRetainedHealthId, List<String> toBeRetiredHealthIds);
}

