package org.openmrs.module.shrclient.util;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class WebClient {

    private static final Logger log = Logger.getLogger(WebClient.class);
    public static final String ZERO_WIDTH_NO_BREAK_SPACE = "\uFEFF";
    public static final String BLANK_CHARACTER = "";
    private String baseUrl;
    private Map<String, String> headers;


    public WebClient(String baseUrl, Map<String, String> headers) {
        this.baseUrl = baseUrl;
        this.headers = headers;
    }


    public String get(String path) throws IdentityUnauthorizedException {
        String url = getUrl(path);
        log.debug("HTTP getEncounters url: " + url);
        try {
            HttpGet request = new HttpGet(URI.create(url));

            return execute(request);
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (IOException e) {
            log.error("Error during http getEncounters. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    public String post(String path, String data, String contentType) throws IdentityUnauthorizedException {
        String url = getUrl(path);
        log.debug("HTTP post url: " + url);
        try {
            HttpPost request = new HttpPost(URI.create(url));
            StringEntity entity = new StringEntity(data);
            entity.setContentType(contentType);
            request.setEntity(entity);
            return execute(request);
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (IOException e) {
            log.error("Error during http post. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    public String put(String path, String data, String contentType) throws IdentityUnauthorizedException {
        String url = getUrl(path);
        log.debug("HTTP post url: " + url);
        try {
            HttpPut request = new HttpPut(URI.create(url));
            StringEntity entity = new StringEntity(data);
            entity.setContentType(contentType);
            request.setEntity(entity);
            return execute(request);
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (IOException e) {
            log.error("Error during http post. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    private String execute(final HttpRequestBase request) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            addHeaders(request);

            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(final HttpResponse response) throws IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? parseContentInputAsString(entity) : null;
                    } else if (status == HttpStatus.NOT_FOUND.value()) {
                        return null;
                    } else if (status == HttpStatus.UNAUTHORIZED.value()) {
                        throw new IdentityUnauthorizedException("Identity not authorized");
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            return httpClient.execute(request, responseHandler);
        }
    }

    private String parseContentInputAsString(HttpEntity entity) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
        String inputLine;
        StringBuilder responseString = new StringBuilder();
        while ((inputLine = reader.readLine()) != null) {
            responseString.append(inputLine);
        }
        reader.close();
        return responseString.toString().replace(ZERO_WIDTH_NO_BREAK_SPACE, BLANK_CHARACTER);
    }

    private void addHeaders(HttpRequestBase request) {
        Map<String, String> requestHeaders = getCommonHeaders();
        if (headers != null) {
            requestHeaders.putAll(headers);
        }

        for (String key : requestHeaders.keySet()) {
            request.addHeader(key, requestHeaders.get(key));
        }
    }

    private Map<String, String> getCommonHeaders() {
        Map<String, String> commonHeaders = new HashMap<>();
        commonHeaders.put("accept", "application/json");
        return commonHeaders;
    }

    private String getUrl(String path) {
        return baseUrl + path;
    }

}
