package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.FamilyMemberHistory;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.mapper.model.EmrEncounter;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.fhir.MRSProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRFamilyMemberHistoryMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ApplicationContext springContext;

    @Autowired
    private FHIRFamilyMemberHistoryMapper familyHistoryMapper;

    @Autowired
    private ConceptService conceptService;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapFamilyHistoryResource() throws Exception {
        executeDataSet("testDataSets/shrClientFamilyHistoryTestDS.xml");
        Bundle bundle = (Bundle) new MapperTestHelper().loadSampleFHIREncounter("encounterBundles/stu3/encounterWithFamilyHistory.xml", springContext);
        FamilyMemberHistory familyHistory = (FamilyMemberHistory) FHIRBundleHelper.identifyFirstResourceWithName(bundle, new FamilyMemberHistory().getResourceType().name());
        Encounter encounter = new Encounter();
        EmrEncounter emrEncounter = new EmrEncounter(encounter);

        ShrEncounterBundle encounterComposition = new ShrEncounterBundle(bundle, "98104750156", "shr-enc-id-1");
        familyHistoryMapper.map(familyHistory, emrEncounter, encounterComposition, getSystemProperties("1"));

        assertEquals(1, emrEncounter.getTopLevelObs().size());
        Obs familyHistoryObs = emrEncounter.getTopLevelObs().iterator().next();
        assertEquals(conceptService.getConceptByName(MRS_CONCEPT_NAME_FAMILY_HISTORY), familyHistoryObs.getConcept());
        assertEquals(1, familyHistoryObs.getGroupMembers().size());

        Obs personObs = familyHistoryObs.getGroupMembers().iterator().next();
        assertEquals(conceptService.getConceptByName(MRS_CONCEPT_NAME_PERSON), personObs.getConcept());
        assertEquals(3, personObs.getGroupMembers().size());

        assertEquals(3, personObs.getGroupMembers().size());
        Obs relationshipTypeObs = identifyObsFromConceptName(TR_VALUESET_RELATIONSHIP_TYPE, personObs.getGroupMembers());
        assertEquals(conceptService.getConcept(400), relationshipTypeObs.getValueCoded());

        Obs bornOnObs = identifyObsFromConceptName(MRS_CONCEPT_NAME_BORN_ON, personObs.getGroupMembers());
        assertEquals(DateUtil.parseDate("2074-12-01 00:00:00"), bornOnObs.getValueDate());

        Obs relationshipConditionObs = identifyObsFromConceptName(MRS_CONCEPT_NAME_RELATIONSHIP_CONDITION, personObs.getGroupMembers());
        assertEquals(3, relationshipConditionObs.getGroupMembers().size());

        Obs relationshipNotesObs = identifyObsFromConceptName(MRS_CONCEPT_NAME_RELATIONSHIP_NOTES, relationshipConditionObs.getGroupMembers());
        assertEquals("Some notes", relationshipNotesObs.getValueText());

        Obs relationshipDiagnosisObs = identifyObsFromConceptName(MRS_CONCEPT_NAME_RELATIONSHIP_DIAGNOSIS, relationshipConditionObs.getGroupMembers());
        assertEquals(conceptService.getConcept(301), relationshipDiagnosisObs.getValueCoded());

        Obs onsetAgeObs = identifyObsFromConceptName(MRS_CONCEPT_NAME_ONSET_AGE, relationshipConditionObs.getGroupMembers());
        assertTrue(5 == onsetAgeObs.getValueNumeric());
    }

    private Obs identifyObsFromConceptName(String conceptName, Set<Obs> groupMembers) {
        for (Obs groupMember : groupMembers) {
            if (groupMember.getConcept().getName().getName().equals(conceptName)) {
                return groupMember;
            }
        }
        return null;
    }
}