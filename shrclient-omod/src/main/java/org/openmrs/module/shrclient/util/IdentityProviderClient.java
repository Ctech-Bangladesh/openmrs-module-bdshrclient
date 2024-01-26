package org.openmrs.module.shrclient.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class IdentityProviderClient {
    private static final Logger log = Logger.getLogger(IdentityProviderClient.class);
    private final WebClient webClient;

    public IdentityProviderClient(String baseUrl, Map<String, String> headers) {
        webClient = new WebClient(baseUrl, headers);
    }

    public <T> T post(String url, Map<String, String> data, Class<T> returnType) throws IdentityUnauthorizedException {
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(getNameValuePairs(data));
            String response = webClient.post(url, entity);
            if (StringUtils.isNotBlank(response)) {
                return new ObjectMapper().readValue(response, returnType);
            }
            return null;
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (IOException e) {
            log.error("Error during http post. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    private ArrayList<BasicNameValuePair> getNameValuePairs(Map<String, String> data) {
        ArrayList<BasicNameValuePair> valuePairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            valuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        return valuePairs;
    }
}
