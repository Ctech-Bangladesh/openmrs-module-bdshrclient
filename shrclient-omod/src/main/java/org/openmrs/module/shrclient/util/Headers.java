package org.openmrs.module.shrclient.util;

import org.apache.commons.codec.binary.Base64;
import org.openmrs.module.shrclient.identity.IdentityToken;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Headers {
    public static final String AUTH_HEADER_KEY = "Authorization";
    public static final String AUTH_TOKEN_KEY = "X-Auth-Token";
    public static final String FROM_KEY = "From";
    public static final String ACCEPT_HEADER_KEY = "Accept";
    public static final String CLIENT_ID_KEY = "client_id";
    public static final String FACILITY_API_TOKEN_KEY = "facility.apiToken";
    public static final String FACILITY_CLIENT_ID_KEY = "facility.clientId";
    public static final String FACILITY_EMAIL_KEY = "facility.email";

    public static Map<String, String> getBasicAuthHeader(String user, String password) {
        HashMap<String, String> header = new HashMap<>();
        String auth = user + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("UTF-8")));
        header.put(AUTH_HEADER_KEY, "Basic " + new String(encodedAuth));
        return header;
    }

    public static Map<String, String> getIdentityHeader(IdentityToken token) {
        HashMap<String, String> header = new HashMap<>();
        header.put(AUTH_TOKEN_KEY, token.toString());
        return header;
    }

    public static HashMap<String, String> getHrmAccessTokenHeaders(IdentityToken identityToken, Properties facilityInstanceProperties) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(CLIENT_ID_KEY, facilityInstanceProperties.getProperty(FACILITY_CLIENT_ID_KEY));
        headers.put(AUTH_TOKEN_KEY, identityToken.toString());
        headers.put(FROM_KEY, facilityInstanceProperties.getProperty(FACILITY_EMAIL_KEY));
        return headers;
    }

    public static HashMap<String, String> getHrmIdentityHeaders(Properties facilityInstanceProperties) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(AUTH_TOKEN_KEY, facilityInstanceProperties.getProperty(FACILITY_API_TOKEN_KEY));
        headers.put(CLIENT_ID_KEY, facilityInstanceProperties.getProperty(FACILITY_CLIENT_ID_KEY));
        return headers;
    }
}
