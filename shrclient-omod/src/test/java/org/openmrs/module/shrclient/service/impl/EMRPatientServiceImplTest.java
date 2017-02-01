package org.openmrs.module.shrclient.service.impl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.PatientIdMapping;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.service.EMRPatientDeathService;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemUserService;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EMRPatientServiceImplTest {

    private EMRPatientServiceImpl emrPatientService;

    @Mock
    private BbsCodeService bbsCodeService;

    @Mock
    private PatientService patientService;

    @Mock
    private PersonService personService;

    @Mock
    private IdMappingRepository idMappingRepository;

    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private SystemUserService systemUserService;

    @Mock
    private EMRPatientDeathService emrPatientDeathService;

    @Mock
    private GlobalPropertyLookUpService globalPropertyLookUpService;


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        emrPatientService = new EMRPatientServiceImpl(bbsCodeService, patientService, personService, idMappingRepository, propertiesReader, systemUserService, emrPatientDeathService, globalPropertyLookUpService);
    }

    @Test
    public void shouldNotProcessWhenLastSyncTimeAndServerUpdateTimeIsGreaterThanModifiedTime() throws Exception {
        Date date = new Date();
        String healthId = "123456789";
        String patientMappingId = "987654321";

        Patient patient = new Patient();
        patient.setHealthId(healthId);
        patient.setModifiedTime(date);

        PatientIdMapping patientIdMapping = new PatientIdMapping(patientMappingId, healthId, "URL", DateUtil.addMinutes(date, 50), DateUtil.addMinutes(date, 50), DateUtil.addMinutes(date, 50));

        org.openmrs.Patient openMRSPatient = new org.openmrs.Patient();


        when(idMappingRepository.findByExternalId(healthId, IdMappingType.PATIENT)).thenReturn(patientIdMapping);
        when(patientService.getPatientByUuid(patientIdMapping.getInternalId())).thenReturn(openMRSPatient);

        org.openmrs.Patient updatedEmrPatient = emrPatientService.createOrUpdateEmrPatient(patient);
        assertEquals(openMRSPatient, updatedEmrPatient);

    }
}