package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.dao.FacilityCatchmentRepository;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.mapper.LocationMapper;
import org.openmrs.module.shrclient.model.FRLocationEntry;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.utils.PropertyKeyConstants.FACILITY_REFERENCE_PATH;
import static org.openmrs.module.shrclient.handlers.FacilityPull.*;

public class FacilityPullTest {
    @Mock
    LocationService locationService;
    @Mock
    private PropertiesReader propertiesReader;
    @Mock
    private RestClient frWebClient;
    @Mock
    private ScheduledTaskHistory scheduledTaskHistory;

    @Mock
    private IdMappingsRepository idMappingsRepository;

    @Mock
    private FacilityCatchmentRepository facilityCatchmentRepository;

    @Captor
    private ArgumentCaptor<ArrayList<String>> catchmentsArgumentCaptor;

    private LocationMapper locationMapper = new LocationMapper();

    FRLocationEntry[] locationEntries;
    Properties frProperties;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        locationEntries = getFacilityEntries();
        frProperties = new Properties();
        frProperties.put(FR_PATH_INFO, "list");
        frProperties.put(PropertyKeyConstants.FACILITY_REFERENCE_PATH, "http://hrmtest.dghs.gov.bd/api/1.0/facilities");
    }

    @Test
    public void shouldSyncAllDataWhenFirstTime() throws Exception {
        final String existingLocationUuid = UUID.randomUUID().toString();

        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";

        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", FRLocationEntry[].class)).thenReturn
                (locationEntries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(any(String.class))).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                return getIdMapping(arguments[0].toString(), existingLocationUuid);
            }
        });

        when(locationService.getLocationByUuid(existingLocationUuid)).thenReturn(getFacilityLocation(existingLocationUuid, 100001));
        when(locationService.saveLocation(any(Location.class))).thenReturn(null);


        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);
        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        //TODO: verify what

    }

    @Test
    public void shouldSyncDeltaForSubsequentRun() throws Exception {
        final String existingLocationUuid = UUID.randomUUID().toString();
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";

        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn
                (locationEntries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(any(String.class))).then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                return getIdMapping(arguments[0].toString(), existingLocationUuid);
            }
        });
        when(locationService.getLocationByUuid(existingLocationUuid)).thenReturn(getFacilityLocation(existingLocationUuid, 100001));
        when(locationService.saveLocation(any(Location.class))).thenReturn(null);


        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(scheduledTaskHistory).setFeedUriForLastReadEntryByFeedUri(startsWith("http://hrmtest.dghs.gov.bd/api/1" +
                ".0/facilities/list?offset="), eq(FR_FACILITY_LEVEL_FEED_URI));

    }

    @Test
    public void shouldUpdateExistingLocation() throws Exception {
        String existingLocationUuid = UUID.randomUUID().toString();
        int frLocationEntryId = 10000001;
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";


        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn
                (oneLocationEntry(String.valueOf(frLocationEntryId)));
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(String.valueOf(frLocationEntryId))).thenReturn(getIdMapping(String.valueOf
                (frLocationEntryId), existingLocationUuid));
        when(locationService.getLocationByUuid(existingLocationUuid)).thenReturn(getFacilityLocation(existingLocationUuid,
                frLocationEntryId));
        when(locationService.saveLocation(any(Location.class))).thenReturn(null);

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(idMappingsRepository).findByExternalId(String.valueOf(frLocationEntryId));
        verify(locationService).getLocationByUuid(existingLocationUuid);
        verify(locationService).saveLocation(any(Location.class));
    }

    @Test
    public void shouldCreateNewLocation() throws Exception {
        int frLocationEntryId = 10000001;
        String newLocationUuid = UUID.randomUUID().toString();
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";

        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        FRLocationEntry[] entries = oneLocationEntry(String.valueOf(frLocationEntryId));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn(entries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(String.valueOf(frLocationEntryId))).thenReturn(null);
        when(locationService.saveLocation(any(Location.class))).thenReturn(getFacilityLocation(newLocationUuid, frLocationEntryId));
        when(locationService.getLocationTagByName(SHR_LOCATION_TAG_NAME)).thenReturn(
                new LocationTag(SHR_LOCATION_TAG_NAME, "foo bar baz"));

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        //TODO: verify(propertiesReader, times(3)).getFrProperties();
        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(idMappingsRepository).findByExternalId(String.valueOf(frLocationEntryId));

        ArgumentCaptor<Location> locationArgumentCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locationService).saveLocation(locationArgumentCaptor.capture());
        Location location = locationArgumentCaptor.getValue();
        assertEquals(1, location.getTags().size());
        assertEquals(SHR_LOCATION_TAG_NAME,
                new ArrayList<>(location.getTags()).get(0).getName());

        ArgumentCaptor<IdMapping> idMappingArgumentCaptor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingsRepository).saveMapping(idMappingArgumentCaptor.capture());
        IdMapping idMapping = idMappingArgumentCaptor.getValue();
        assertEquals(String.valueOf(frLocationEntryId), idMapping.getExternalId());
        assertEquals(newLocationUuid, idMapping.getInternalId());
    }

    @Test
    public void shouldSyncMultipleNew() throws Exception {
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn
                (locationEntries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(any(String.class))).thenReturn(null);
        when(locationService.saveLocation(any(Location.class))).thenReturn(getFacilityLocation(UUID.randomUUID().toString(), 100001));
        when(locationService.getLocationTagByName(SHR_LOCATION_TAG_NAME)).thenReturn(
                new LocationTag(SHR_LOCATION_TAG_NAME, "foo bar baz"));

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(locationService).getLocationTagByName(SHR_LOCATION_TAG_NAME);
        verify(idMappingsRepository, times(10)).findByExternalId(any(String.class));
        verify(locationService, times(10)).saveLocation(any(Location.class));
        verify(idMappingsRepository, times(10)).saveMapping(any(IdMapping.class));

    }

    @Test
    public void shouldUpdateNothingIfWeGetNothing() throws Exception {
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";

        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn(new
                FRLocationEntry[]{});
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(locationService.saveLocation(any(Location.class))).thenReturn(getFacilityLocation(UUID.randomUUID().toString(), 100001));
        when(locationService.getLocationTagByName(SHR_LOCATION_TAG_NAME)).thenReturn(
                new LocationTag(SHR_LOCATION_TAG_NAME, "foo bar baz"));

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(idMappingsRepository, times(0)).findByExternalId(any(String.class));
        verify(locationService, times(1)).getLocationTagByName(SHR_LOCATION_TAG_NAME);
        verify(locationService, times(0)).saveLocation(any(Location.class));
        verify(idMappingsRepository, times(0)).saveMapping(any(IdMapping.class));

    }

    @Test
    public void shouldCreateIndividualUriForMappedIds() throws Exception {
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";

        when(propertiesReader.getFrBaseUrl()).thenReturn("http://hrmtest.dghs.gov.bd/api/1.0/facilities");
        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn
                (oneLocationEntry("100001"));
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(locationService.saveLocation(any(Location.class))).thenReturn(getFacilityLocation(UUID.randomUUID().toString(), 100001));
        when(locationService.getLocationTagByName(SHR_LOCATION_TAG_NAME)).thenReturn(
                new LocationTag(SHR_LOCATION_TAG_NAME, "foo bar baz"));

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        ArgumentCaptor<IdMapping> captor = ArgumentCaptor.forClass(IdMapping.class);
        verify(idMappingsRepository).saveMapping(captor.capture());
        assertEquals("http://hrmtest.dghs.gov.bd/api/1.0/facilities/100001.json", captor.getValue().getUri());
    }

    @Test
    public void shouldCreateFacilityCatchmentMappingsForNewFacility() throws IOException {
        int frLocationEntryId = 10000001;
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";

        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        FRLocationEntry[] entries = oneLocationEntryWithCatchments(String.valueOf(frLocationEntryId));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn(entries);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(String.valueOf(frLocationEntryId))).thenReturn(null);
        when(locationService.saveLocation(any(Location.class))).thenReturn(getFacilityLocation(UUID.randomUUID().toString(),
                frLocationEntryId));
        when(locationService.getLocationTagByName(SHR_LOCATION_TAG_NAME)).thenReturn(
                new LocationTag(SHR_LOCATION_TAG_NAME, "foo bar baz"));

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        //TODO: verify(propertiesReader, times(3)).getFrProperties();
        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(idMappingsRepository).findByExternalId(String.valueOf(frLocationEntryId));
        verify(facilityCatchmentRepository).saveMappings(frLocationEntryId, getCatchments());

        ArgumentCaptor<Integer> idArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(facilityCatchmentRepository).saveMappings(idArgumentCaptor.capture(), catchmentsArgumentCaptor.capture());
        ArrayList<String> catchments = catchmentsArgumentCaptor.getValue();
        assertEquals(3, catchments.size());
    }

    @Test
    public void shouldCreateFacilityCatchmentMappingsForExistingFacility() throws IOException {
        int frLocationEntryId = 10000001;
        String existingLocationUuid = UUID.randomUUID().toString();
        String feedUri = "http://hrmtest.dghs.gov.bd/api/1.0/facilities/list?offset=0&limit=100&updatedSince=2000-12-31 23:55:55";


        when(propertiesReader.getFrProperties()).thenReturn(frProperties);
        when(propertiesReader.getFrBaseUrl()).thenReturn(frProperties.getProperty(FACILITY_REFERENCE_PATH));
        when(frWebClient.get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class)).thenReturn
                (oneLocationEntryWithCatchments(String.valueOf(frLocationEntryId)));
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI)).thenReturn(feedUri);
        when(idMappingsRepository.findByExternalId(String.valueOf(frLocationEntryId))).thenReturn(getIdMapping(String.valueOf
                (frLocationEntryId), existingLocationUuid));
        when(locationService.getLocationByUuid(existingLocationUuid)).thenReturn(getFacilityLocation(UUID.randomUUID().toString(),
                frLocationEntryId));
        when(locationService.saveLocation(any(Location.class))).thenReturn(null);

        FacilityPull facilityPull = new FacilityPull(propertiesReader, frWebClient,
                locationService, scheduledTaskHistory, idMappingsRepository, locationMapper, facilityCatchmentRepository);

        facilityPull.synchronize();

        verify(frWebClient).get("list?offset=0&limit=100&updatedSince=2000-12-31%2023:55:55", FRLocationEntry[].class);
        verify(scheduledTaskHistory).getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        verify(idMappingsRepository).findByExternalId(String.valueOf(frLocationEntryId));
        verify(locationService).getLocationByUuid(existingLocationUuid);
        verify(locationService).saveLocation(any(Location.class));
        verify(facilityCatchmentRepository).saveMappings(frLocationEntryId, getCatchments());


        ArgumentCaptor<Integer> idArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(facilityCatchmentRepository).saveMappings(idArgumentCaptor.capture(), catchmentsArgumentCaptor.capture());
        ArrayList<String> catchments = catchmentsArgumentCaptor.getValue();
        assertEquals(3, catchments.size());
    }

    private IdMapping getIdMapping(String externalId, String existingLocationUuid) {
        return new IdMapping(existingLocationUuid, externalId, "fr_location", StringUtils.EMPTY);
    }

    private Location getFacilityLocation(String locationUuid, int locationId) {
        Location location = new Location();
        location.setUuid(locationUuid);
        location.setLocationId(locationId);
        return location;
    }


    public FRLocationEntry[] getFacilityEntries() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        URL resource = URLClassLoader.getSystemResource("FRResponse/ResponseFromFacilityRegistry.json");
        final String response = FileUtils.readFileToString(new File(resource.getPath()));
        return mapper.readValue(response, FRLocationEntry[].class);
    }

    public FRLocationEntry[] oneLocationEntry(String id) throws IOException {
        FRLocationEntry frLocationEntry = new FRLocationEntry();
        frLocationEntry.setId(id);
        frLocationEntry.setName("bar");
        frLocationEntry.setActive("0");
        return new FRLocationEntry[]{frLocationEntry};
    }

    public FRLocationEntry[] oneLocationEntryWithCatchments(String id) throws IOException {
        FRLocationEntry frLocationEntry = new FRLocationEntry();
        frLocationEntry.setId(id);
        frLocationEntry.setName("bar");
        frLocationEntry.setActive("0");
        frLocationEntry.getProperties().setCatchments(getCatchments());
        return new FRLocationEntry[]{frLocationEntry};
    }

    private List<String> getCatchments() {
        ArrayList<String> catchments = new ArrayList<>();
        catchments.add("123");
        catchments.add("223344");
        catchments.add("33");
        return catchments;
    }

}