package org.openmrs.module.shrclient.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.shrclient.web.controller.MciPatientSearchRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface MCIPatientLookupService extends OpenmrsService {

    @Authorized(value = {"National Registry"}, requireAll = true)
    public Object searchPatientInRegistry(MciPatientSearchRequest request);

    @Authorized(value = {"National Registry"}, requireAll = true)
    public Object downloadPatient(MciPatientSearchRequest request);
}
