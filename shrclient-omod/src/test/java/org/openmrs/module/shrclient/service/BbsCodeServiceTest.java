package org.openmrs.module.shrclient.service;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BbsCodeServiceTest {

    private static BbsCodeService service;

    @Before
    public void setup() throws Exception {
        service = new BbsCodeService();
    }

    @Test
    public void shouldGetGenderCodes() {
        assertEquals("1", service.getGenderCode("M"));
        assertEquals("2", service.getGenderCode("F"));
        assertEquals("3", service.getGenderCode("O"));
    }

    @Test
    public void shouldGetGenderConceptNames() {
        assertEquals("M", service.getGenderConcept("1"));
        assertEquals("F", service.getGenderConcept("2"));
        assertEquals("O", service.getGenderConcept("3"));
    }

    @Test
    public void shouldGetEducationCodes() {
        assertEquals("00", service.getEducationCode("Nursery"));
        assertEquals("01", service.getEducationCode("1st Std"));
        assertEquals("02", service.getEducationCode("2nd Std"));
        assertEquals("03", service.getEducationCode("3rd Std"));
        assertEquals("04", service.getEducationCode("4th Std"));
        assertEquals("05", service.getEducationCode("5th Std"));
        assertEquals("06", service.getEducationCode("6th Std"));
        assertEquals("07", service.getEducationCode("7th Std"));
        assertEquals("08", service.getEducationCode("8th Std"));
        assertEquals("09", service.getEducationCode("9th Std"));
        assertEquals("10", service.getEducationCode("10th Std or Equivalent"));
        assertEquals("11", service.getEducationCode("Higher Secondary or Equivalent"));
        assertEquals("12", service.getEducationCode("Graduate or Equivalent"));
        assertEquals("13", service.getEducationCode("Post Graduate"));
        assertEquals("14", service.getEducationCode("Medical"));
        assertEquals("15", service.getEducationCode("Engineering"));
        assertEquals("16", service.getEducationCode("Vocational Education"));
        assertEquals("17", service.getEducationCode("Technical Education"));
        assertEquals("18", service.getEducationCode("Nursing"));
        assertEquals("19", service.getEducationCode("Other Education"));
    }

    @Test
    public void shouldGetEducationConceptNames() {
        assertEquals("Nursery", service.getEducationConceptName("00"));
        assertEquals("1st Std", service.getEducationConceptName("01"));
        assertEquals("2nd Std", service.getEducationConceptName("02"));
        assertEquals("3rd Std", service.getEducationConceptName("03"));
        assertEquals("4th Std", service.getEducationConceptName("04"));
        assertEquals("5th Std", service.getEducationConceptName("05"));
        assertEquals("6th Std", service.getEducationConceptName("06"));
        assertEquals("7th Std", service.getEducationConceptName("07"));
        assertEquals("8th Std", service.getEducationConceptName("08"));
        assertEquals("9th Std", service.getEducationConceptName("09"));
        assertEquals("10th Std or Equivalent", service.getEducationConceptName("10"));
        assertEquals("Higher Secondary or Equivalent", service.getEducationConceptName("11"));
        assertEquals("Graduate or Equivalent", service.getEducationConceptName("12"));
        assertEquals("Post Graduate", service.getEducationConceptName("13"));
        assertEquals("Medical", service.getEducationConceptName("14"));
        assertEquals("Engineering", service.getEducationConceptName("15"));
        assertEquals("Vocational Education", service.getEducationConceptName("16"));
        assertEquals("Technical Education", service.getEducationConceptName("17"));
        assertEquals("Nursing", service.getEducationConceptName("18"));
        assertEquals("Other Education", service.getEducationConceptName("19"));
    }

    @Test
    public void shouldGetOccupationCodes() {
        assertEquals("01", service.getOccupationCode("Physical scientist and related technician"));
        assertEquals("02", service.getOccupationCode("Engineer and architect"));
        assertEquals("08", service.getOccupationCode("Statistician, mathematician, systems analyst and related stuff"));
        assertEquals("17", service.getOccupationCode("Actor, singer and dancer"));
        assertEquals("30", service.getOccupationCode("Government executive"));
        assertEquals("61", service.getOccupationCode("Farmer"));
        assertEquals("77", service.getOccupationCode("Food and beverage manufacturer"));
        assertEquals("88", service.getOccupationCode("Goldsmith"));
    }

    @Test
    public void shouldGetOccupationConceptNames() {
        assertEquals("Physical scientist and related technician", service.getOccupationConceptName("01"));
        assertEquals("Engineer and architect", service.getOccupationConceptName("02"));
        assertEquals("Statistician, mathematician, systems analyst and related stuff", service.getOccupationConceptName("08"));
        assertEquals("Actor, singer and dancer", service.getOccupationConceptName("17"));
        assertEquals("Government executive", service.getOccupationConceptName("30"));
        assertEquals("Farmer", service.getOccupationConceptName("61"));
        assertEquals("Food and beverage manufacturer", service.getOccupationConceptName("77"));
        assertEquals("Goldsmith", service.getOccupationConceptName("88"));
    }

}