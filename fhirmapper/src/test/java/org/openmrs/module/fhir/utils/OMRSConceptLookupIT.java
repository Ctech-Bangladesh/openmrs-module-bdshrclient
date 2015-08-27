package org.openmrs.module.fhir.utils;


import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class OMRSConceptLookupIT extends BaseModuleWebContextSensitiveTest {

    private static final String CONCEPT_URI = "http://www.bdshr-tr.com/concepts/";
    private static final String REF_TERM_URI = "http://www.bdshr-tr.com/refterms/";
    private static final String VALUE_SET_URI = "http://www.bdshr-tr.com/tr/vs/";
    @Autowired
    private ConceptService conceptService;
    
    @Autowired
    private OMRSConceptLookup omrsConceptLookup;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/omrsConceptLookupTestDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldFindConceptFromCoding_ThatHasConcept() {
        List<CodingDt> codings = asList(buildCoding(REF_TERM_URI, "1101", "A001", "some concept"),
                buildCoding(REF_TERM_URI, "1102", "B001", "some ref term 2"),
                buildCoding(CONCEPT_URI, "101", "101", "Fever"));
        Concept concept = omrsConceptLookup.findConceptByCode(codings);
        assertNotNull(concept);
        assertEquals(conceptService.getConcept(398).getUuid(), concept.getUuid());
    }

    @Test
    public void shouldFindConceptFromCodingThatHasReferenceTermsWithMatchingConceptPreferredName() {
        List<CodingDt> codings = asList(buildCoding(REF_TERM_URI, "1101", "A001", "xyz concept"),
                buildCoding(REF_TERM_URI, "1102", "B001", "Fever"));
        Concept concept = omrsConceptLookup.findConceptByCode(codings);
        assertNotNull(concept);
        assertEquals(conceptService.getConcept(398).getUuid(), concept.getUuid());
    }

    @Test
    @Ignore
    //TODO : should we do this?
    public void shouldFindConceptFromCodingThatHasReferenceTermsWithoutAnyMatchingConceptPreferredName() {
        List<CodingDt> codings = asList(buildCoding(REF_TERM_URI, "1101", "A001", "xyz concept"),
                buildCoding(REF_TERM_URI, "1102", "B001", "pqr concept"));
        Concept concept = omrsConceptLookup.findConceptByCode(codings);
        assertNotNull(concept);
        assertTrue(concept.getName().getName().equals("xyz concept") || concept.getName().getName().equals("pqr concept"));
    }

    @Test
    public void shouldMapConceptGivenCodeOfFullySpecifiedName() {
        assertEquals(conceptService.getConcept(401), omrsConceptLookup.findConceptFromValueSetCode("http://tr.com/Value-Set-Concept", "Value Set Answer 1"));
    }

    @Test
    public void shouldMapConceptGivenCodeOfShortName() {
        assertEquals(conceptService.getConcept(401), omrsConceptLookup.findConceptFromValueSetCode("http://tr.com/Value-Set-Concept", "VSA-1"));
    }

    @Test
    public void shouldMapConceptGivenCodeOfReferenceTerm(){
        assertEquals(conceptService.getConcept(401), omrsConceptLookup.findConceptFromValueSetCode("http://tr.com/Value-Set-Concept", "VSA-ref"));
    }

    @Test
    public void shouldMatchBasedOnShortName() {
        Concept conceptWithShortName = conceptService.getConcept(401);
        assertTrue(omrsConceptLookup.shortNameFound(conceptWithShortName, "VSA-1"));
        assertFalse(omrsConceptLookup.shortNameFound(conceptWithShortName, "irrelevant short name"));
    }

    @Test
    public void shouldMatchBasedOnReferenceTerm(){
        Concept conceptWithReferenceTerm = conceptService.getConcept(401);
        assertTrue(omrsConceptLookup.referenceTermCodeFound(conceptWithReferenceTerm, "VSA-ref"));
        assertFalse(omrsConceptLookup.referenceTermCodeFound(conceptWithReferenceTerm, "invalid ref term"));

    }

    @Test
    public void shouldMapConceptFromValueSetUrl() throws Exception {
        Concept mappedConcept = omrsConceptLookup.findConceptByCode(asList(buildCoding(VALUE_SET_URI,
                "Value-Set-Concept",
                "Value Set Answer 1",
                "Value Set Answer 1")));
        assertEquals(conceptService.getConcept(401), mappedConcept);
    }

    @Test
    public void shouldGetTrValueSetConceptFromGlobalProperty() throws Exception {
        Concept routeOfAdministrationConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.ROUTE_OF_ADMINISTRATION);
        assertEquals(conceptService.getConcept(402), routeOfAdministrationConcept);
    }

    @Test
    public void shouldGetTrValueSetConceptFromNameIfGlobalPropertyNotPresent() throws Exception {
        Concept relationshipTypeConcept = omrsConceptLookup.findTRConceptOfType(TrValueSetType.RELATIONSHIP_TYPE);
        assertEquals(conceptService.getConcept(403), relationshipTypeConcept);
    }

    private CodingDt buildCoding(String uri, String externalId, String code, String display) {
        final CodingDt coding = new CodingDt();
        coding.setSystem(uri + externalId);
        coding.setCode(code);
        coding.setDisplay(display);
        return coding;
    }

}