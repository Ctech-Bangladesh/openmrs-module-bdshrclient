package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Date;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FHIRObservationValueMapperIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private FHIRObservationValueMapper valueMapper;

    @Autowired
    private ConceptService conceptService;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldMapStringDt() throws Exception {
        String value = "No problems";
        StringType stringDt = new StringType(value);
        Obs obs = valueMapper.map(stringDt, new Obs());

        assertEquals(value, obs.getValueText());
    }

    @Test
    public void shouldMapQuantityDt() throws Exception {
        Quantity quantityDt = new Quantity(200.0);
        Obs obs = valueMapper.map(quantityDt, new Obs());

        assertThat(obs.getValueNumeric(), is(200.0));
    }

    @Test
    public void shouldMapDateTimeDt() throws Exception {
        Date date = new Date();
        DateTimeType dateTimeDt = new DateTimeType(date);
        Obs obs = valueMapper.map(dateTimeDt, new Obs());

        assertEquals(date, obs.getValueDatetime());
    }

    @Test
    public void shouldMapDateDt() throws Exception {
        Date date = new Date();
        DateType dateDt = new DateType(date);
        Obs obs = valueMapper.map(dateDt, new Obs());

        assertEquals(date, obs.getValueDate());
    }

    @Test
    public void shouldMapBooleanCodings() throws Exception {
        Concept concept = new Concept(10);
        concept.setDatatype(conceptService.getConceptDatatypeByName("Boolean"));
        CodeableConcept noBooleanCoding = new CodeableConcept();
        noBooleanCoding.addCoding().setSystem("http://hl7.org/fhir/v2/0136").setCode("N");

        Obs noObs = new Obs();
        noObs.setConcept(concept);
        noObs = valueMapper.map(noBooleanCoding, noObs);

        assertFalse(noObs.getValueBoolean());

        CodeableConcept yesBooleanCoding = new CodeableConcept();
        yesBooleanCoding.addCoding().setSystem("http://hl7.org/fhir/v2/0136").setCode("Y");
        Obs yesObs = new Obs();
        yesObs.setConcept(concept);
        yesObs = valueMapper.map(yesBooleanCoding, yesObs);

        assertTrue(yesObs.getValueBoolean());
    }

    @Test
    public void shouldMapDrugCodings() throws Exception {
        executeDataSet("testDataSets/fhirObservationValueMapperTestDs.xml");

        CodeableConcept codeableConceptDt = new CodeableConcept();
        codeableConceptDt.addCoding().setSystem("http://tr.com/ws/rest/v1/tr/drugs/drugs/104").setCode("104");

        Obs obs = valueMapper.map(codeableConceptDt, new Obs());

        assertEquals(conceptService.getDrug(301), obs.getValueDrug());
        assertEquals(conceptService.getConcept(301), obs.getValueCoded());
    }

    @Test
    public void shouldMapConceptCodings() throws Exception {
        executeDataSet("testDataSets/fhirObservationValueMapperTestDs.xml");

        CodeableConcept codeableConceptDt = new CodeableConcept();
        codeableConceptDt.addCoding().setSystem("http://tr.com/ws/rest/v1/tr/concepts/102").setCode("102");

        Obs obs = valueMapper.map(codeableConceptDt, new Obs());

        assertEquals(conceptService.getConcept(301), obs.getValueCoded());
        assertNull(obs.getValueDrug());
    }
}