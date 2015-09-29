package org.openmrs.module.fhir.mapper.bundler.condition;

import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.StringDt;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir.mapper.FHIRProperties;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.text.SimpleDateFormat;
import java.util.Date;

import static java.lang.Boolean.FALSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        IDatatype value = observationValueMapper.map(obs);
        assertTrue(value instanceof DateTimeDt);
        java.util.Date actualDate = ((DateTimeDt) value).getValue();
        assertEquals(obsDate, actualDate);
    }

    @Test
    public void shouldMapNumericValues() throws Exception {
        Obs obs = new Obs();
        ConceptNumeric concept = new ConceptNumeric();
        String units = "units";
        concept.setUnits(units);
        concept.setDatatype(conceptService.getConceptDatatypeByName("Numeric"));
        obs.setConcept(concept);
        double valueNumeric = 10.0;
        obs.setValueNumeric(valueNumeric);
        IDatatype value = observationValueMapper.map(obs);
        assertTrue(value instanceof QuantityDt);
        QuantityDt quantity = (QuantityDt) value;
        assertTrue(quantity.getValue().doubleValue() == valueNumeric);
        assertEquals(units, quantity.getUnits());
    }

    @Test
    public void shouldMapTextValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Text"));
        obs.setConcept(concept);
        String valueText = "Hello";
        obs.setValueText(valueText);
        IDatatype value = observationValueMapper.map(obs);
        assertTrue(value instanceof StringDt);
        assertEquals(valueText, ((StringDt) value).getValue());
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
        IDatatype value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConceptDt);
        assertEquals(conceptName, ((CodeableConceptDt) value).getCoding().get(0).getDisplay());
    }

    @Test
    public void shouldMapBooleanValues() throws Exception {
        Obs obs = new Obs();
        Concept concept = new Concept();
        concept.setDatatype(conceptService.getConceptDatatypeByName("Boolean"));
        obs.setConcept(concept);
        obs.setValueBoolean(FALSE);
        IDatatype value = observationValueMapper.map(obs);
        assertTrue(value instanceof CodeableConceptDt);
        CodingDt codingDt = ((CodeableConceptDt) value).getCoding().get(0);
        assertEquals(FHIRProperties.FHIR_YES_NO_INDICATOR_URL, codingDt.getSystem());
        assertEquals(FHIRProperties.FHIR_NO_INDICATOR_CODE, codingDt.getCode());
        assertEquals(FHIRProperties.FHIR_NO_INDICATOR_DISPLAY, codingDt.getDisplay());
    }
}