package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.fhir.FHIRProperties.FHIR_CONDITION_CATEGORY_COMPLAINT_CODE;
import static org.openmrs.module.fhir.FHIRProperties.FHIR_CONDITION_CATEGORY_URL;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChiefComplaintMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    ChiefComplaintMapper chiefComplaintMapper;

    @Autowired
    EncounterService encounterService;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldCreateFHIRConditionFromChiefComplaint() throws Exception {
        executeDataSet("testDataSets/shrClientChiefComplaintTestDS.xml");
        Encounter encounter = new Encounter();
        encounter.setSubject(new Reference());
        encounter.addParticipant().setIndividual(new Reference());
        org.openmrs.Encounter openMrsEncounter = encounterService.getEncounter(36);

        List<FHIRResource> complaintResources = chiefComplaintMapper.map(openMrsEncounter.getObsAtTopLevel(false).iterator().next(), new FHIREncounter(encounter), getSystemProperties("1"));
        Assert.assertFalse(complaintResources.isEmpty());
        Assert.assertEquals(1, complaintResources.size());
        Condition resource = (Condition) complaintResources.get(0).getResource();
        Coding codingFirstRep = resource.getCategoryFirstRep().getCodingFirstRep();
        assertEquals(FHIR_CONDITION_CATEGORY_URL, codingFirstRep.getSystem());
        assertEquals(FHIR_CONDITION_CATEGORY_COMPLAINT_CODE, codingFirstRep.getCode());
    }

    @Test
    public void checkIfMapperCanHandleChiefComplaintObservation() throws Exception {
        executeDataSet("testDataSets/shrClientChiefComplaintTestDS.xml");
        Obs chiefComplaintObservation = encounterService.getEncounter(36).getObsAtTopLevel(false).iterator().next();
        assertTrue(chiefComplaintMapper.canHandle(chiefComplaintObservation));
    }
}
