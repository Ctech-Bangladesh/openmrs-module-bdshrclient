package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.instance.model.Encounter;
import org.hl7.fhir.instance.model.ResourceReference;
import org.junit.Assert;
import org.junit.Test;
import org.openmrs.api.EncounterService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChiefComplaintMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    ChiefComplaintMapper chiefComplaintMapper;

    @Autowired
    EncounterService encounterService;

    @Test
    public void shouldCreateFHIRConditionFromChiefComplaint() throws Exception {
        executeDataSet("testDataSets/shrClientChiefComplaintTestDS.xml");
        Encounter encounter = new Encounter();
        encounter.setIndication(new ResourceReference());
        encounter.setSubject(new ResourceReference());
        encounter.addParticipant().setIndividual(new ResourceReference());
        org.openmrs.Encounter openMrsEncounter = encounterService.getEncounter(36);

        List<FHIRResource> complaintResources = chiefComplaintMapper.map(openMrsEncounter.getObsAtTopLevel(false).iterator().next(), encounter, getSystemProperties("1"));
        Assert.assertFalse(complaintResources.isEmpty());
        Assert.assertEquals(1, complaintResources.size());
    }
}
