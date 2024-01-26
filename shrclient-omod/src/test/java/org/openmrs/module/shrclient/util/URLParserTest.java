package org.openmrs.module.shrclient.util;

import org.junit.Test;

import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;

import static org.junit.Assert.*;
import static org.openmrs.module.shrclient.util.URLParser.parseURL;

public class URLParserTest {

    @Test
    public void testParseURL() throws Exception {
        String url = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/ward?offset=0&limit=100&updatedSince=0000-00-00 00:00:00";
        Map<String, String> parameters = parseURL(new URL(URLDecoder.decode(url,"UTF-8")));
        assertEquals(parameters.size(), 3);
        assertEquals("0", parameters.get("offset"));
        assertEquals("100", parameters.get("limit"));
        assertEquals("0000-00-00 00:00:00", parameters.get("updatedSince"));
    }
}