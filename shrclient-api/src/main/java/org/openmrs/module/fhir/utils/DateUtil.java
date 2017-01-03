package org.openmrs.module.fhir.utils;

import org.apache.commons.lang3.time.DateUtils;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.apache.commons.lang3.time.DateUtils.addSeconds;

public class DateUtil {
    public static final String FHIR_ISO_DATE_IN_MILLIS_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    public static final String UTC_DATE_IN_MILLIS_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSZ"; //2015-02-17 11:32:24.638+0530
    public static final String UTC_DATE_IN_SECS_FORMAT = "yyyy-MM-dd HH:mm:ssZ"; //2015-02-17 11:35:17+0530
    public static final String SIMPLE_DATE_WITH_SECS_FORMAT = "yyyy-MM-dd HH:mm:ss"; //2015-02-17 11:35:36
    public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd"; //2015-02-17
    public static final String SIMPLE_DATE_FORMAT_DATE_MONTH_YEAR_ORDER = "dd-MM-yyyy"; //17-02-2015
    public static final String ISO_DATE_IN_MILLIS_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"; //2015-02-17T11:36:11.587+0530
    public static final String ISO_DATE_IN_SECS_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ"; //2015-02-17T11:37:16+0530
    public static final String ISO_DATE_IN_HOUR_MIN_FORMAT = "yyyy-MM-dd'T'HH:mmZ"; //2015-02-17T11:37+0530
    public static final String ISO_8601_DATE_IN_MILLIS_FORMAT1 = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ"; //2015-02-17T11:36:11.587+05:30
    public static final String ISO_8601_DATE_IN_SECS_FORMAT1 = "yyyy-MM-dd'T'HH:mm:ssZZ"; //2015-02-17T11:37:16+05:30
    public static final String ISO_8601_DATE_IN_HOUR_MIN_FORMAT1 = "yyyy-MM-dd'T'HH:mmZZ"; //2015-02-17T11:37+05:30
    public static final String ISO_8601_DATE_IN_HOUR_MIN_FORMAT2 = "yyyy-MM-dd'T'HH:mmXXX"; //2015-02-17T11:37+05:30
    public static final String ISO_8601_DATE_IN_SECS_FORMAT2 = "yyyy-MM-dd'T'HH:mm:ssXXX"; //2015-02-17T11:37:16+05:30
    public static final String ISO_8601_DATE_IN_MILLIS_FORMAT2 = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"; //2015-02-17T11:36:11.587+05:30

    public static final String UTC_DATE_MILLIS_TZD_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX"; //2011-04-15T20:08:18.032Z
    public static final String UTC_DATE_IN_SECS_TZD_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX"; //2011-04-15T20:08:18Z;
    public static final String UTC_DATE_IN_MIN_TZD_FORMAT = "yyyy-MM-dd'T'HH:mmX"; //2011-04-15T20:08Z
    public static final String UTC_DATE_IN_HOUR_TZD_FORMAT = "yyyy-MM-dd'T'HHX"; //2011-04-15T20Z
    public static final String UTC_DATE_IN_DATE_TZD_FORMAT = "yyyy-MM-dd'T'X"; //2011-04-15TZ
    public static final String UTC_DATE_IN_SIMPLE_TZD_FORMAT = "yyyy-MM-ddX"; //2011-04-15Z

    public static final String[] DATE_FORMATS = new String[]{
            ISO_DATE_IN_MILLIS_FORMAT, ISO_DATE_IN_SECS_FORMAT, FHIR_ISO_DATE_IN_MILLIS_FORMAT,
            ISO_DATE_IN_HOUR_MIN_FORMAT,
            UTC_DATE_IN_MILLIS_FORMAT, UTC_DATE_IN_SECS_FORMAT,
            SIMPLE_DATE_WITH_SECS_FORMAT, SIMPLE_DATE_FORMAT,
            UTC_DATE_MILLIS_TZD_FORMAT, UTC_DATE_IN_SECS_TZD_FORMAT,
            UTC_DATE_IN_MIN_TZD_FORMAT, UTC_DATE_IN_HOUR_TZD_FORMAT,
            UTC_DATE_IN_DATE_TZD_FORMAT, UTC_DATE_IN_SIMPLE_TZD_FORMAT,
            ISO_8601_DATE_IN_MILLIS_FORMAT1, ISO_8601_DATE_IN_SECS_FORMAT1,
            ISO_8601_DATE_IN_HOUR_MIN_FORMAT1, ISO_8601_DATE_IN_HOUR_MIN_FORMAT2,
            ISO_8601_DATE_IN_SECS_FORMAT2, ISO_8601_DATE_IN_MILLIS_FORMAT2};

    public static String getCurrentTimeInUTCString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(UTC_DATE_IN_MILLIS_FORMAT);
        return dateFormat.format(new Date());
    }

    public static int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public static int getYearOf(Date date) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(date);
        return instance.get(Calendar.YEAR);
    }

    public static Date parseDate(String date, String... formats) throws ParseException {
        return org.apache.commons.lang3.time.DateUtils.parseDate(date, formats);
    }

    public static Date parseDate(String date) {
        try {
            return parseDate(date, DateUtil.DATE_FORMATS);
        } catch (ParseException e) {
            throw new RuntimeException("invalid date:" + date);
        }
    }

    public static String toUTCString(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(UTC_DATE_IN_MILLIS_FORMAT);
        //dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    public static String toISOString(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(ISO_DATE_IN_MILLIS_FORMAT);
        //dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    public static String getCurrentTimeInISOString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(ISO_DATE_IN_MILLIS_FORMAT);
        return dateFormat.format(new Date());
    }

    public static String toDateString(Date date, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        return dateFormat.format(date);
    }

    public static boolean isLaterThan(Date firstDate, Date secondDate) {
        Calendar firstTime = Calendar.getInstance();
        firstTime.setTime(firstDate);
        firstTime.set(Calendar.MILLISECOND, 0);

        Calendar secondTime = Calendar.getInstance();
        secondTime.setTime(secondDate);
        secondTime.set(Calendar.MILLISECOND, 0);

        return firstTime.after(secondTime);
    }

    public static boolean isEqualTo(Date date1, Date date2) {
        if (date1 != null && date2 != null) {
            Date truncatedDate1 = DateUtils.setMilliseconds(date1, 0);
            Date truncatedDate2 = DateUtils.setMilliseconds(date2, 0);
            return truncatedDate1.equals(truncatedDate2);
        }
        return date1 == null && date2 == null;
    }

    public static Date getDateFromTimestamp(Timestamp timestamp){
        return timestamp!=null ? new Date(timestamp.getTime()) : null;
    }
    
    public static Date aSecondBefore(Date date) {
        return addSeconds(date, -1);
    }

    public static Date aSecondAfter(Date date) {
        return addSeconds(date, 1);
    }

    public static Date addMinutes(Date date, int minutes) {
        return org.apache.commons.lang3.time.DateUtils.addMinutes(date, minutes);
    }
}