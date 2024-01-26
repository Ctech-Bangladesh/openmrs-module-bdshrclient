package org.openmrs.module.shrclient.util;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.model.Patient;

import java.io.IOException;
import java.util.Map;

public class RestClient {

    private static final Logger log = Logger.getLogger(RestClient.class);
    private final ObjectMapper mapper;
    private final WebClient webClient;

    public RestClient(String baseUrl, Map<String, String> headers) {
        webClient = new WebClient(baseUrl, headers);
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public <T> T get(String url, Class<T> returnType) throws IdentityUnauthorizedException {
        try {
            String response = webClient.get(url);

            if (StringUtils.isNotBlank(response)) {
                return mapper.readValue(response, returnType);
            } else {
                return null;
            }
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (IOException e) {
            log.error("Error Mapping response to : " + url, e);
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            log.error("Error while connecting the Server : " + url, e);
            throw e;
        }
    }

    public <T> T post(String url, Object data, Class<T> returnType) throws IdentityUnauthorizedException {
        try {
            String requestBody = mapper.writeValueAsString(data);
            StringEntity entity;
            if (data instanceof Patient) {
                entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
            } else {
                entity = new StringEntity(requestBody);
            }
            entity.setContentType("application/json");
            String response = webClient.post(url, entity);
            if (StringUtils.isNotBlank(response)) {
                return mapper.readValue(response, returnType);
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

    public <T> T put(String url, Object data, Class<T> returnType) throws IdentityUnauthorizedException {
        try {
            String requestBody = mapper.writeValueAsString(data);
            StringEntity entity;
            if (data instanceof Patient) {
                entity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
            } else {
                entity = new StringEntity(requestBody);
            }
            entity.setContentType("application/json");

            String response = webClient.put(url, entity);
            if (StringUtils.isNotBlank(requestBody)) {
                return mapper.readValue(response, returnType);
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
}
