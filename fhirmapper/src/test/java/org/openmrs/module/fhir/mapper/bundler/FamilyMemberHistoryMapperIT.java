package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.api.EncounterService;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.openmrs.module.fhir.FHIRProperties.UCUM_UNIT_FOR_YEARS;
import static org.openmrs.module.fhir.FHIRProperties.UCUM_URL;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FamilyMemberHistoryMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private EncounterService encounterService;

    @Autowired
    private FamilyMemberHistoryMapper familyMemberHistoryMapper;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldCreateFHIRFamilyHistoryFromOpenMrsFamilyHistory() throws Exception {
        executeDataSet("testDataSets/shrClientFamilyHistoryTestDS.xml");
        Encounter encounter = new Encounter();
        Reference subject = new Reference();
        subject.setReference("http://mci.com/patient/hid");
        encounter.setSubject(subject);
        encounter.addParticipant().setIndividual(new Reference());
        org.openmrs.Encounter openMrsEncounter = encounterService.getEncounter(36);

        List<FHIRResource> familyHistoryResources = familyMemberHistoryMapper.map(openMrsEncounter.getObsAtTopLevel(false).iterator().next(), new FHIREncounter(encounter), getSystemProperties("1"));
        assertFalse(familyHistoryResources.isEmpty());
        assertEquals(1, familyHistoryResources.size());

        FamilyMemberHistory familyMemberHistoryResource = (FamilyMemberHistory) TestFhirFeedHelper.getFirstResourceByType(new FamilyMemberHistory().getResourceType().name(), familyHistoryResources).getResource();
        assertEquals(subject, familyMemberHistoryResource.getPatient());
        assertFalse(familyMemberHistoryResource.getIdentifier().isEmpty());

        assertRelationship(familyMemberHistoryResource);
        assertBornOn(familyMemberHistoryResource);
        assertRelationshipCondition(familyMemberHistoryResource);
    }

    private void assertRelationshipCondition(FamilyMemberHistory familyMemberHistory) {
        assertEquals(1, familyMemberHistory.getCondition().size());
        FamilyMemberHistory.FamilyMemberHistoryConditionComponent familyMemberCondition = familyMemberHistory.getCondition().get(0);
        assertEquals("some notes", familyMemberCondition.getNote().get(0).getText());
        assertOnsetAge(familyMemberCondition);
        assertConditionType(familyMemberCondition);
    }

    private void assertConditionType(FamilyMemberHistory.FamilyMemberHistoryConditionComponent familyMemberCondition) {
        assertEquals(1, familyMemberCondition.getCode().getCoding().size());
        Coding type = familyMemberCondition.getCode().getCoding().get(0);
        assertEquals("3", type.getCode());
        assertEquals("http://tr.com/openmrs/ws/rest/v1/tr/concept/3", type.getSystem());
    }

    private void assertOnsetAge(FamilyMemberHistory.FamilyMemberHistoryConditionComponent familyMemberCondition) {
        Age onset = (Age) familyMemberCondition.getOnset();
        assertEquals(12, onset.getValue().intValue());
        assertEquals(UCUM_UNIT_FOR_YEARS, onset.getUnit());
        assertEquals(UCUM_URL, onset.getSystem());
    }

    private void assertBornOn(FamilyMemberHistory familyMemberHistory) {
        Date bornOn = ((DateType) familyMemberHistory.getBorn()).getValue();
        assertEquals(DateUtil.parseDate("1978-02-15 00:00:00"), bornOn);
    }

    private void assertRelationship(FamilyMemberHistory familyMemberHistory) {
        assertEquals(1, familyMemberHistory.getRelationship().getCoding().size());

        Coding relationship = familyMemberHistory.getRelationship().getCoding().get(0);
        assertEquals("FTH", relationship.getCode());
        assertEquals("Father", relationship.getDisplay());
        assertEquals("http://localhost:9080/openmrs/ws/rest/v1/tr/vs/Relationship-Type", relationship.getSystem());
    }
}