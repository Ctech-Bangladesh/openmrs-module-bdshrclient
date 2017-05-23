package org.openmrs.module.shrclient.dao;

import org.junit.After;
import org.junit.Test;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static org.junit.Assert.assertEquals;


public class IdMappingRepositoryIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    IdMappingRepository idMappingRepository;

    @Test
    public void shouldGetAllIdMappingsByHealthId() throws Exception {
        executeDataSet("testDataSets/idMappingDS.xml");

        assertIdMapping(idMappingRepository.findByHealthId("Health_id", IdMappingType.ENCOUNTER).get(0),
                new IdMapping("75e04d42-3ca8-11e3-bf2b-0800271c1b75", "75e04d42-3ca8-11e3-bf2b-0800271c1b76", IdMappingType.ENCOUNTER,
                        "/patients/Health_id/encounters", new Date()));
        assertIdMapping(idMappingRepository.findByHealthId("Health_id", IdMappingType.DIAGNOSIS).get(0),
                new IdMapping("76e04d42-123s-11e3-bf2b-0800271c1b75", "76e04d42-3ca8-lk87-bf2b-0800271c1b76", IdMappingType.DIAGNOSIS,
                        "/patients/Health_id/encounters/enc_id#Condition/76e04d42-123s-11e3-bf2b-0800271c1b75", new Date()));

        assertIdMapping(idMappingRepository.findByHealthId("Health_id2", IdMappingType.MEDICATION_ORDER).get(0),
                new IdMapping("juydg81f-1yz9-4xv3-bz88-8z22a1dx1zt","597d18f5-bc92-4c43-b0fa-526fe6181d0e:e30e5355-cca5-4eb5-947f-0", IdMappingType.MEDICATION_ORDER,
                        "/patients/Health_id2/encounters/597d18f5-bc92-4c43-b0fa-526fe6181d0e#MedicationRequest/e30e5355-cca5-4eb5-947f-0", new Date()));
        assertIdMapping(idMappingRepository.findByHealthId("Health_id", IdMappingType.PROCEDURE_REQUEST).get(0),
                new IdMapping("76e04d42-3ca8-11e3-bf2b-0800271c1b75", "76e04d42-3ca8-11e3-bf2b-0800271c1b76", IdMappingType.PROCEDURE_REQUEST,
                        "/patients/Health_id/encounters/enc_id#ProcedureRequest/76e04d42-3ca8-11e3-bf2b-0800271c1b75", new Date()));
    }

    @Test
    public void shouldUpdateAllIdMappingsByHealthId() throws Exception {
        executeDataSet("testDataSets/idMappingDS.xml");
        idMappingRepository.replaceHealthId("Health_id", "new_HID");

        assertIdMapping(idMappingRepository.findByHealthId("new_HID", IdMappingType.ENCOUNTER).get(0),
                new IdMapping("75e04d42-3ca8-11e3-bf2b-0800271c1b75", "75e04d42-3ca8-11e3-bf2b-0800271c1b76", IdMappingType.ENCOUNTER,
                        "/patients/new_HID/encounters", new Date()));
        assertIdMapping(idMappingRepository.findByHealthId("new_HID", IdMappingType.PROCEDURE_REQUEST).get(0),
                new IdMapping("76e04d42-3ca8-11e3-bf2b-0800271c1b75", "76e04d42-3ca8-11e3-bf2b-0800271c1b76", IdMappingType.PROCEDURE_REQUEST,
                        "/patients/new_HID/encounters/enc_id#ProcedureRequest/76e04d42-3ca8-11e3-bf2b-0800271c1b75", new Date()));
        assertIdMapping(idMappingRepository.findByHealthId("new_HID", IdMappingType.DIAGNOSIS).get(0),
                new IdMapping("76e04d42-123s-11e3-bf2b-0800271c1b75", "76e04d42-3ca8-lk87-bf2b-0800271c1b76", IdMappingType.DIAGNOSIS,
                        "/patients/new_HID/encounters/enc_id#Condition/76e04d42-123s-11e3-bf2b-0800271c1b75", new Date()));


    }

    private void assertIdMapping(IdMapping idMapping, IdMapping expectedIdMapping) {
        assertEquals(expectedIdMapping.getInternalId(), idMapping.getInternalId());
        assertEquals(expectedIdMapping.getExternalId(), idMapping.getExternalId());
        assertEquals(expectedIdMapping.getType(), idMapping.getType());
        assertEquals(expectedIdMapping.getUri(), idMapping.getUri());
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}