package org.openmrs.module.shrclient.dao;

import org.junit.After;
import org.junit.Test;
import org.openmrs.module.shrclient.model.FacilityCatchment;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FacilityCatchmentRepositoryIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    FacilityCatchmentRepository facilityCatchmentRepository;

    @Test
    public void shouldGetCatchmentsForFacility() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(3, facilityCatchments.size());
    }

    @Test
    public void shouldGetNothingForFacilityNotPresent() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(99);
        assertEquals(0, facilityCatchments.size());
    }

    @Test
    public void shouldGetFacilitiesForCatchment() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByCatchment("3020");
        assertEquals(2, facilityCatchments.size());
    }

    @Test
    public void shouldGetNoCatchmentsForFacilityWithoutCatchments() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(2);
        assertEquals(0, facilityCatchments.size());
    }

    @Test
    public void shouldSaveMappingsAfresh() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(3, facilityCatchments.size());

        ArrayList<String> catchments = new ArrayList<>();
        catchments.add("890978");
        facilityCatchmentRepository.saveMappings(1, catchments);
        facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(1, facilityCatchments.size());
    }

    @Test
    public void shouldNotSaveDuplicateMappings() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(3, facilityCatchments.size());

        ArrayList<String> catchments = new ArrayList<>();
        catchments.add("890978");
        catchments.add("890978");
        catchments.add("890978");
        facilityCatchmentRepository.saveMappings(1, catchments);
        facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(1, facilityCatchments.size());
    }

    @Test
    public void shouldDeleteMappingsIfCatchmentsNotReceived() throws Exception {
        executeDataSet("testDataSets/facilityCatchmentsDS.xml");
        List<FacilityCatchment> facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(3, facilityCatchments.size());

        facilityCatchmentRepository.saveMappings(1, new ArrayList<String>());
        facilityCatchments = facilityCatchmentRepository.findByFacilityLocationId(1);
        assertEquals(0, facilityCatchments.size());
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();

    }
}