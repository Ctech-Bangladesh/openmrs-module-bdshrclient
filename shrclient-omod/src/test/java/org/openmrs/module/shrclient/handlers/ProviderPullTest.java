package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.Provider;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.PersonService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.mapper.ProviderMapper;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.ProviderEntry;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.shrclient.handlers.ProviderPull.PR_FEED_URI;

public class ProviderPullTest {
    @Mock
    private PropertiesReader propertiesReader;
    @Mock
    private RestClient prClient;
    @Mock
    private ScheduledTaskHistory scheduledTaskHistory;
    @Mock
    private ProviderService providerService;
    @Mock
    private IdMappingRepository idMappingRepository;
    @Mock
    private PersonService personService;

    private ProviderEntry[] providerEntries;
    private ProviderPull providerPull;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        ProviderMapper providerMapper = new ProviderMapper(providerService, idMappingRepository, personService);
        providerPull = new ProviderPull(propertiesReader, prClient, scheduledTaskHistory, providerMapper);
        providerEntries = getProviderEntries();
        Properties properties = new Properties();
        properties.put("pr.pathInfo", "list");
        properties.put("pr.providerUrlFormat", "/providers/%s.json");
        properties.put("pr.referenceUrl", "http://hrmtest.dghs.gov.bd/api/1.0/providers");

        when(propertiesReader.getPrProperties()).thenReturn(properties);
        when(propertiesReader.getPrBaseUrl()).thenReturn("http://hrmtest.dghs.gov.bd/api/1.0/providers");
        ProviderAttributeType organizationAttributeType = new ProviderAttributeType();
        organizationAttributeType.setName("Organization");
        when(providerService.getAllProviderAttributeTypes(false)).thenReturn(asList(organizationAttributeType));
    }

    @Test
    public void shouldSyncNewProvidersFromProviderRegistry() throws Exception {
        when(prClient.get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class))
                .thenReturn(providerEntries);
        providerPull.synchronize();

        verify(prClient, times(1)).get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI);
        verify(providerService, times(5)).saveProvider(any(Provider.class));
        verify(idMappingRepository, times(5)).findByExternalId(anyString(), eq(IdMappingType.PROVIDER));
        String format = new SimpleDateFormat(DateUtil.SIMPLE_DATE_FORMAT).format(new Date());
        verify(scheduledTaskHistory, times(1)).setFeedUriForLastReadEntryByFeedUri(contains("list?offset=0&limit=100&updatedSince=" + format), eq(PR_FEED_URI));
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId("23", PR_FEED_URI);
    }

    @Test
    public void shouldFindNextOffsetIfAlreadySyncedOnce() throws Exception {
        when(prClient.get("list?offset=100&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class))
                .thenReturn(providerEntries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI))
                .thenReturn("http://hrmtest.dghs.gov.bd/api/1.0/providers/list?offset=100&limit=100&updatedSince=0000-00-00%2000:00:00");
        providerPull.synchronize();

        verify(prClient, times(1)).get("list?offset=100&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI);
        verify(providerService, times(5)).saveProvider(any(Provider.class));
        verify(idMappingRepository, times(5)).findByExternalId(anyString(), eq(IdMappingType.PROVIDER));
        String format = new SimpleDateFormat(DateUtil.SIMPLE_DATE_FORMAT).format(new Date());
        verify(scheduledTaskHistory, times(1)).setFeedUriForLastReadEntryByFeedUri(contains("/providers/list?offset=0&limit=100&updatedSince=" + format), eq(PR_FEED_URI));
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId("23", PR_FEED_URI);
    }

    @Test
    public void shouldSyncUpdatesFromProviderRegistry() throws Exception {
        when(prClient.get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class))
                .thenReturn(providerEntries);
        when(providerService.getProviderByIdentifier(anyString())).thenReturn(new Provider());
        providerPull.synchronize();

        verify(prClient, times(1)).get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI);
        verify(providerService, times(5)).saveProvider(any(Provider.class));
        verify(idMappingRepository, times(5)).findByExternalId(anyString(), eq(IdMappingType.PROVIDER));
        String format = new SimpleDateFormat(DateUtil.SIMPLE_DATE_FORMAT).format(new Date());
        verify(scheduledTaskHistory, times(1)).setFeedUriForLastReadEntryByFeedUri(contains("/providers/list?offset=0&limit=100&updatedSince=" + format), eq(PR_FEED_URI));
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId("23", PR_FEED_URI);
    }

    @Test
    public void shouldNotUpdateIfWeGetNoUpdates() throws Exception {
        when(prClient.get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class))
                .thenReturn(null);
        providerPull.synchronize();

        verify(prClient, times(1)).get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", ProviderEntry[].class);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI);
        verify(providerService, times(0)).saveProvider(any(Provider.class));
        verify(providerService, times(0)).getProviderByIdentifier(anyString());
        verify(scheduledTaskHistory, times(0)).setFeedUriForLastReadEntryByFeedUri(anyString(), anyString());
        verify(scheduledTaskHistory, times(0)).setLastReadEntryId(anyString(), anyString());
    }

    @Test
    public void shouldStopSyncIfNoOfEntriesSyncedMoreThanMaxNumberOfEntriesToBeSynced() throws Exception {
        ProviderEntry[] providerEntryList = new ProviderEntry[0];
        for (int i = 0; i<20; i++) {
            providerEntryList = ArrayUtils.addAll(providerEntryList, providerEntries);
        }
        when(prClient.get(anyString(), eq(ProviderEntry[].class))).thenReturn(providerEntryList);
        providerPull.synchronize();

        verify(prClient, times(10)).get(anyString(), eq(ProviderEntry[].class));
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(PR_FEED_URI);
        verify(providerService, times(1000)).saveProvider(any(Provider.class));
        verify(idMappingRepository, times(1000)).findByExternalId(anyString(), eq(IdMappingType.PROVIDER));
        verify(scheduledTaskHistory, times(1)).setFeedUriForLastReadEntryByFeedUri(contains("list?offset=1000&limit=100&updatedSince=0000-00-00%2000:00:00"), eq(PR_FEED_URI));
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId("23", PR_FEED_URI);
    }

    private ProviderEntry[] getProviderEntries() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        URL resource = URLClassLoader.getSystemResource("Pr/prResponse.json");
        final String response = FileUtils.readFileToString(new File(resource.getPath()));
        return mapper.readValue(response, ProviderEntry[].class);
    }
}