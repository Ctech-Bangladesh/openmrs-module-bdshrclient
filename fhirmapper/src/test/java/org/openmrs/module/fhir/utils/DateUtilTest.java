package org.openmrs.module.fhir.utils;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DateUtilTest {

    @Test
    public void testIsLaterThan() throws Exception {
        Date firstDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:20:123");
        Date secondDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:20:245");
        assertFalse(DateUtil.isLaterThan(firstDate, secondDate));

        firstDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:21:123");
        secondDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:20:245");
        assertTrue(DateUtil.isLaterThan(firstDate, secondDate));

        firstDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:19:123");
        secondDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SSS").parse("21-06-2015 15:30:20:245");
        assertFalse(DateUtil.isLaterThan(firstDate, secondDate));
    }

    @Test
    public void shouldIgnoreMillisecondsWhileComparingDates() throws Exception {
        Date date1 = DateUtil.parseDate("2015-01-29T10:41:48.560+06:00");
        Date date2 = DateUtil.parseDate("2015-01-29T10:41:47.401+06:00");
        assertFalse(DateUtil.isEqualTo(date1, date2));

        date1 = DateUtil.parseDate("2015-01-29T10:41:48.560+06:00");
        date2 = DateUtil.parseDate("2015-01-29T10:41:48.401+06:00");
        assertTrue(DateUtil.isEqualTo(date1, date2));

        date1 = DateUtil.parseDate("2015-01-29T10:41:48.560+06:00");
        date2 = null;
        assertFalse(DateUtil.isEqualTo(date1, date2));

        date1 = null;
        date2 = null;
        assertTrue(DateUtil.isEqualTo(date1, date2));
    }
}