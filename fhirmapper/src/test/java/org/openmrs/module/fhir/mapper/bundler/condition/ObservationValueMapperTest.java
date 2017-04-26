package org.openmrs.module.fhir.mapper.bundler.condition;

import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.mapper.bundler.ObservationValueMapper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static java.lang.Boolean.FALSE;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.openmrs.module.fhir.MapperTestHelper.containsCoding;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ObservationValueMapperTest extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ObservationValueMapper observationValueMapper;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapDateValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Date"));
        obs.setConcept(concept);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date obsDate = dateFormat.parse("2014-03-12");
        obs.setValueDate(obsDate);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof DateType);
        java.util.Date actualDate = ((DateType) value).getValue();
        assertEquals(obsDate, actualDate);
    }

    @Test
    public void shouldMapNumericValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept(123);
        concept.setConceptClass(conceptService.getConceptClassByName("Misc"));
        concept.setDatatype(conceptService.getConceptDatatypeByName("Numeric"));
        concept.setFullySpecifiedName(new ConceptName("Test Concept", Locale.ENGLISH));
        ConceptNumeric conceptNumeric = new ConceptNumeric(concept);
        String units = "Kg";
        conceptNumeric.setUnits(units);
        conceptService.saveConcept(concept);
        conceptService.saveConcept(conceptNumeric);
        obs.setConcept(conceptNumeric);
        double valueNumeric = 10.0;
        obs.setValueNumeric(valueNumeric);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof Quantity);
        Quantity quantity = (Quantity) value;
        assertTrue(quantity.getValue().doubleValue() == valueNumeric);
        assertEquals(units, quantity.getUnit());
    }

    @Test
    public void shouldMapTextValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Text"));
        obs.setConcept(concept);
        String valueText = "Hello";
        obs.setValueText(valueText);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof StringType);
        assertEquals(valueText, ((StringType) value).getValue());
    }

    @Test
    public void shouldMapCodedValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Coded"));
        obs.setConcept(concept);
        Concept codedConcept = new Concept(10);
        String conceptName = "Concept";
        codedConcept.addName(new ConceptName(conceptName, conceptService.getLocalesOfConceptNames().iterator().next()));
        obs.setValueCoded(codedConcept);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConcept);
        assertEquals(conceptName, ((CodeableConcept) value).getCoding().get(0).getDisplay());
    }

    @Test
    public void shouldMapBooleanValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Boolean"));
        obs.setConcept(concept);
        obs.setValueBoolean(FALSE);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConcept);
        Coding codingDt = ((CodeableConcept) value).getCoding().get(0);
        assertEquals(FHIRProperties.FHIR_YES_NO_INDICATOR_URL, codingDt.getSystem());
        assertEquals(FHIRProperties.FHIR_NO_INDICATOR_CODE, codingDt.getCode());
        assertEquals(FHIRProperties.FHIR_NO_INDICATOR_DISPLAY, codingDt.getDisplay());
    }

    @Test
    public void shouldMapCodedDrugValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Coded"));
        obs.setConcept(concept);
        Concept codedConcept = new Concept(10);
        String conceptName = "Concept 1";
        codedConcept.addName(new ConceptName(conceptName, conceptService.getLocalesOfConceptNames().iterator().next()));
        Drug codedDrug = new Drug(10);
        String drugName = "Drug 1";
        codedDrug.setConcept(codedConcept);
        codedDrug.setName(drugName);
        obs.setValueCoded(codedConcept);
        obs.setValueDrug(codedDrug);
        Type value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConcept);
        assertTrue(containsCoding(((CodeableConcept) value).getCoding(), null, null, drugName));
    }
}