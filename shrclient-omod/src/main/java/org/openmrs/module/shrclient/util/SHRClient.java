package org.openmrs.module.shrclient.util;

import ca.uhn.fhir.parser.IParser;
import com.sun.syndication.feed.atom.Content;
import com.sun.syndication.feed.atom.Entry;
import com.sun.syndication.feed.atom.Feed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.WireFeedInput;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;
import org.hl7.fhir.dstu3.model.Bundle;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.web.controller.dto.EncounterEvent;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SHRClient {

    private static final Logger log = Logger.getLogger(RestClient.class);

    private String baseUrl;
    private Map<String, String> headers;

    public SHRClient(String baseUrl, Map<String, String> headers) {
        this.baseUrl = baseUrl;
        this.headers = headers;
    }

    @SuppressWarnings("unchecked")
    public List<EncounterEvent> getEncounters(final String url) throws IdentityUnauthorizedException {
        try {
            Map<String, String> requestHeaders = new HashMap<>(headers);
            requestHeaders.put("accept", "application/atom+xml");
            WebClient webClient = new WebClient(baseUrl, requestHeaders);
            String response = webClient.get(url);
            WireFeedInput input = new WireFeedInput();
            Feed feed = (Feed) input.build(new StringReader(response));
            List<Entry> entries = feed.getEntries();
            List<EncounterEvent> encounterEvents = new ArrayList<>();
            for (Entry entry : entries) {
                String entryContent = getEntryContent(entry);
                EncounterEvent event = new EncounterEvent();
                event.setTitle(entry.getTitle());
                event.setCategories(entry.getCategories());
                event.addContent(getBundle(entryContent));
                encounterEvents.add(event);
            }
            return encounterEvents;

        } catch (FeedException e) {
            log.error("Error fetching encounters for : " + url, e);
            throw new RuntimeException(e);
        }
    }

    private Bundle getBundle(String entryContent) {
        IParser xmlParser = FhirBundleContextHolder.getFhirContext().newXmlParser();
        return xmlParser.parseResource(Bundle.class, entryContent);
    }

    private String getEntryContent(Entry entry) {
        if (entry.getContents().isEmpty()) {
            return null;
        }
        String value = ((Content) (entry.getContents().get(0))).getValue();
        return value.replaceFirst("^<!\\[CDATA\\[", "").replaceFirst("\\]\\]>$", "");
    }

    public String post(final String url, Bundle bundle) throws IdentityUnauthorizedException {
        try {
            StringEntity entity = getPayload(bundle);
            WebClient webClient = new WebClient(baseUrl, headers);
            log.debug(String.format("Posting data %s to url %s", bundle, url));
            return webClient.post(url, entity);
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (Exception e) {
            log.error("Error during http post. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    public String put(final String url, Bundle bundle) throws IdentityUnauthorizedException {
        try {
            StringEntity entity = getPayload(bundle);
            WebClient webClient = new WebClient(baseUrl, headers);
            log.debug(String.format("Put request %s to url %s", bundle, url));
            return webClient.put(url, entity);
        } catch (IdentityUnauthorizedException e) {
            log.error("Unauthorized identity. URL: " + url, e);
            throw e;
        } catch (Exception e) {
            log.error("Error during http post. URL: " + url, e);
            throw new RuntimeException(e);
        }
    }

    private StringEntity getPayload(Bundle bundle) throws Exception {
        IParser xmlParser = FhirBundleContextHolder.getFhirContext().newXmlParser();
        String bundleXML = xmlParser.encodeResourceToString(bundle);
        StringEntity entity = new StringEntity(bundleXML);
        entity.setContentType("application/xml;charset=UTF-8");
        return entity;
    }
}
