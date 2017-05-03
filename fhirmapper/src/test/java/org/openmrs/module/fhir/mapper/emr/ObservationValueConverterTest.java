package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.*;
import org.junit.Test;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.mapper.emr.ObservationValueConverter.*;
import static org.openmrs.module.fhir.mapper.emr.ObservationValueConverter.convertToText;


public class ObservationValueConverterTest {
    @Test
    public void shouldConvertANullTypeToEmptyString() throws Exception {
        assertTrue(convertToText(null).isEmpty());
    }

    @Test
    public void shouldConvertADateType() throws Exception {
        Date date = new SimpleDateFormat("dd-MM-yyyy").parse("17-06-2015T00:00:00+05:30");
        String text = convertToText(date);
        assertEquals("17 Jun 2015", text);
    }

    @Test
    public void shouldConvertAQuantityDtType() throws Exception {
        Quantity quantity = new Quantity();
        assertEquals("", convertToText(quantity));

        DecimalType decimalValue = new DecimalType();
        decimalValue.setValue(new BigDecimal(12));
        quantity.setValue(decimalValue.getValue());

        assertEquals("12", convertToText(quantity));

        StringType units = new StringType();
        units.setValue("mg");
        quantity.setUnit(units.getValue());

        assertEquals("12 mg", convertToText(quantity));
    }

    @Test
    public void shouldConvertACodeableConceptDtWhenItHasCoding() throws Exception {
        CodeableConcept codeableConcept = new CodeableConcept();
        assertTrue(convertToText(codeableConcept).isEmpty());

        Coding coding = codeableConcept.addCoding();
        String fever = "Fever";
        coding.setDisplay(fever);

        assertEquals(fever, convertToText(codeableConcept));
    }

    @Test
    public void shouldGiveTextValueForACodeableConceptDtWithoutCoding() throws Exception {
        CodeableConcept codeableConcept = new CodeableConcept();
        assertTrue(convertToText(codeableConcept).isEmpty());

        String fever = "Fever";
        codeableConcept.setText(fever);

        assertEquals(fever, convertToText(codeableConcept));
    }

    @Test
    public void shouldConvertARatioDt() throws Exception {
        Ratio ratio = new Ratio();
        assertEquals("0", convertToText(ratio));

        ratio.setNumerator(new Quantity(12));
        ratio.setDenominator(new Quantity(24));

        assertEquals("12/24", convertToText(ratio));
    }

    @Test
    public void shouldConvertAPeriod() throws Exception {
        Period period1 = new Period();
        assertEquals(INVALID_PERIOD, convertToText(period1));

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        Date startDate = simpleDateFormat.parse("17-06-2015T00:00:00+05:30");
        period1.setStart(startDate, TemporalPrecisionEnum.DAY);
        assertEquals("17 Jun 2015", convertToText(period1));

        Period period2 = new Period();
        Date endDate = simpleDateFormat.parse("17-06-2016T00:00:00+05:30");
        period2.setEnd(endDate, TemporalPrecisionEnum.DAY);
        assertEquals(PERIOD_START_UNKNOWN + " - 17 Jun 2016", convertToText(period2));

        period2.setStart(startDate, TemporalPrecisionEnum.DAY);
        assertEquals("17 Jun 2015 - 17 Jun 2016", convertToText(period2));
    }

    @Test
    public void shouldConvertASampledData() throws Exception {
        SampledData sampledData = new SampledData();
        String hello = "Hello";
        sampledData.setData(hello);

        assertEquals(hello, convertToText(sampledData));
    }

    @Test
    public void shouldConvertARange() throws Exception {
        Range range1 = new Range();
        SimpleQuantity upperLimit = new SimpleQuantity();
        upperLimit.setValue(12);
        range1.setHigh(upperLimit);
        assertEquals(" - 12", convertToText(range1));

        SimpleQuantity lowerLimit = new SimpleQuantity();
        lowerLimit.setValue(6);
        range1.setLow(lowerLimit);

        assertEquals("6 - 12", convertToText(range1));

    }

    @Test
    public void shouldConvertATimingDtRepeatHavingFrequency() throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        Date startDate = simpleDateFormat.parse("17-06-2015T00:00:00+05:30");
        Date endDate = simpleDateFormat.parse("17-06-2016T00:00:00+05:30");

        Period period = new Period();
        period.setStart(startDate, TemporalPrecisionEnum.DAY);
        period.setEnd(endDate, TemporalPrecisionEnum.DAY);


        Timing.TimingRepeatComponent repeat = new Timing.TimingRepeatComponent();
        repeat.setBounds(period);
        repeat.setFrequency(2);
        repeat.setPeriod(1);
        repeat.setPeriodUnit(Timing.UnitsOfTime.D);

        Timing timing = new Timing();
        timing.setRepeat(repeat);

        assertEquals("2 time(s) in 1 Day. Duration:- 17 Jun 2015 - 17 Jun 2016", convertToText(timing));
    }

    @Test
    public void shouldConvertATimingDtRepeatHavingWhen() throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
        Date startDate = simpleDateFormat.parse("17-06-2015T00:00:00+05:30");
        Date endDate = simpleDateFormat.parse("17-06-2016T00:00:00+05:30");

        Period period = new Period();
        period.setStart(startDate, TemporalPrecisionEnum.DAY);
        period.setEnd(endDate, TemporalPrecisionEnum.DAY);


        Timing.TimingRepeatComponent repeat = new Timing.TimingRepeatComponent();
        repeat.setBounds(period);
        repeat.setWhen(asList(Timing.EventTiming.ACM));
        repeat.setPeriod(1);
        repeat.setPeriodUnit(Timing.UnitsOfTime.H);

        Timing timing = new Timing();
        timing.setRepeat(repeat);

        assertEquals("1 Hour before breakfast. Duration:- 17 Jun 2015 - 17 Jun 2016", convertToText(timing));
    }
}