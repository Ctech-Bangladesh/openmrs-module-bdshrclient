package org.openmrs.module.shrclient.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.mapper.AddressHierarchyEntryMapper;
import org.openmrs.module.shrclient.model.AddressHierarchyEntryTranslation;
import org.openmrs.module.shrclient.model.LRAddressHierarchyEntry;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.shrclient.handlers.LocationPull.*;

public class LocationPullTest {

    private String fileContainingDivisionLevelResponse = "DivisionLevelResponseFromLocationRegistry";
    private String fileContainingDistrictLevelResponse = "DistrictLevelResponseFromLocationRegistry";
    private String fileContainingUpazilaLevelResponse = "UpazilaLevelResponseFromLocationRegistry";
    private String fileContainingPaurasavaLevelResponse = "PaurasavaLevelResponseFromLocationRegistry";
    private String fileContainingUnionLevelResponse = "UnionLevelResponseFromLocationRegistry";
    private String fileContainingWardLevelResponse = "WardLevelResponseFromLocationRegistry";

    @Mock
    PropertiesReader propertiesReader;

    @Mock
    RestClient lrWebClient;

    @Mock
    AddressHierarchyService addressHierarchyService;

    @Mock
    ScheduledTaskHistory scheduledTaskHistory;

    @Mock
    AddressHierarchyEntryMapper addressHierarchyEntryMapper;

    @Mock
    AddressHierarchyEntryTranslationRepository addressHierarchyEntryTranslationRepository;

    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForDivisions;
    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForDistricts;
    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForUpazilas;
    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForPaurasavas;
    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForUnions;
    LRAddressHierarchyEntry[] lrAddressHierarchyEntriesForWards;
    Properties properties;


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        lrAddressHierarchyEntriesForDivisions = getAddressHierarchyEntries(fileContainingDivisionLevelResponse);
        lrAddressHierarchyEntriesForDistricts = getAddressHierarchyEntries(fileContainingDistrictLevelResponse);
        lrAddressHierarchyEntriesForUpazilas = getAddressHierarchyEntries(fileContainingUpazilaLevelResponse);
        lrAddressHierarchyEntriesForPaurasavas = getAddressHierarchyEntries(fileContainingPaurasavaLevelResponse);
        lrAddressHierarchyEntriesForUnions = getAddressHierarchyEntries(fileContainingUnionLevelResponse);
        lrAddressHierarchyEntriesForWards = getAddressHierarchyEntries(fileContainingWardLevelResponse);
        properties = new Properties();
        properties.put(PropertyKeyConstants.LOCATION_REFERENCE_PATH, "http://hrmtest.dghs.gov.bd/api/1.0");
        properties.put(LR_DIVISIONS_PATH_INFO, "list/division");
        properties.put(LR_DISTRICTS_PATH_INFO, "list/district");
        properties.put(LR_UPAZILAS_PATH_INFO, "list/upazila");
        properties.put(LR_PAURASAVAS_PATH_INFO, "list/paurasava");
        properties.put(LR_UNIONS_PATH_INFO, "list/union");
        properties.put(LR_WARDS_PATH_INFO, "list/ward");
    }

    @Test
    public void shouldSynchronizeAllTheUpdatesForFirstTime() throws Exception {

        String feedUriForLastReadEntryAtDivisionLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/division?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String feedUriForLastReadEntryAtDistrictLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/district?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String feedUriForLastReadEntryAtUpazilaLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/upazila?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String feedUriForLastReadEntryAtPaurasavaLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/paurasava?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String feedUriForLastReadEntryAtUnionLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/union?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String feedUriForLastReadEntryAtWardLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/ward?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";

        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_DIVISIONS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtDivisionLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_DISTRICTS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtDistrictLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_UPAZILAS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtUpazilaLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_PAURASAVAS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtPaurasavaLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_UNIONS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtUnionLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_WARDS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtWardLevel);
        when(propertiesReader.getLrProperties()).thenReturn(properties);
        when(propertiesReader.getLrBaseUrl()).thenReturn("http://hrmtest.dghs.gov.bd/api/1.0/locations");

        String divisionContextPath = "list/division?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String districtContextPath = "list/district?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String upazilaContextPath = "list/upazila?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String paurasavaContextPath = "list/paurasava?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String unionContextPath = "list/union?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";
        String wardContextPath = "list/ward?offset=0&limit=100&updatedSince=0000-00-00%2000:00:00";

        when(lrWebClient.get(divisionContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForDivisions);
        when(lrWebClient.get(districtContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForDistricts);
        when(lrWebClient.get(upazilaContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForUpazilas);
        when(lrWebClient.get(paurasavaContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForPaurasavas);
        when(lrWebClient.get(unionContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForUnions);
        when(lrWebClient.get(wardContextPath, LRAddressHierarchyEntry[].class)).thenReturn(lrAddressHierarchyEntriesForWards);

        String lastReadEntryIdForDivision = "60";
        String lastReadEntryIdForDistrict = "3061";
        String lastReadEntryIdForUpazila = "100610";
        String lastReadEntryIdForPaurasava = "10066987";
        String lastReadEntryIdForUnion = "1004099913";
        String lastReadEntryIdForWard = "100409991501";

        LocationPull locationPull = new LocationPull(propertiesReader, lrWebClient, addressHierarchyService, scheduledTaskHistory, addressHierarchyEntryMapper, addressHierarchyEntryTranslationRepository);
        locationPull.synchronize();

        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_DIVISIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_DISTRICTS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_UPAZILAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_PAURASAVAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_UNIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_WARDS_LEVEL_FEED_URI);
        //verify(propertiesReader, times(12)).getLrProperties();

        verify(lrWebClient).get(divisionContextPath, LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(districtContextPath, LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(upazilaContextPath, LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(paurasavaContextPath, LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(unionContextPath, LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(wardContextPath, LRAddressHierarchyEntry[].class);

        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForDivision, LR_DIVISIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForDistrict, LR_DISTRICTS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForUpazila, LR_UPAZILAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForPaurasava, LR_PAURASAVAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForUnion, LR_UNIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).setLastReadEntryId(lastReadEntryIdForWard, LR_WARDS_LEVEL_FEED_URI);
    }

    @Test
    public void shouldNotSynchronizeAnyUpdatesWhenAllUpdatesHaveTakenPlaceBeforeLastExecutionDataAndTime() throws Exception {

        String feedUriForLastReadEntryAtDivisionLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/division?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";
        String feedUriForLastReadEntryAtDistrictLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/district?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";
        String feedUriForLastReadEntryAtUpazilaLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/upazila?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";
        String feedUriForLastReadEntryAtPaurasavaLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/paurasava?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";
        String feedUriForLastReadEntryAtUnionLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/union?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";
        String feedUriForLastReadEntryAtWardLevel = "http://hrmtest.dghs.gov.bd/api/1.0/locations/list/ward?offset=0&limit=100&updatedSince=2014-11-11 12:00:00";

        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_DIVISIONS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtDivisionLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_DISTRICTS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtDistrictLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_UPAZILAS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtUpazilaLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_PAURASAVAS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtPaurasavaLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_UNIONS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtUnionLevel);
        when(scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(LR_WARDS_LEVEL_FEED_URI)).thenReturn(feedUriForLastReadEntryAtWardLevel);
        when(propertiesReader.getLrProperties()).thenReturn(properties);
        when(propertiesReader.getLrBaseUrl()).thenReturn("http://hrmtest.dghs.gov.bd/api/1.0/locations");

        String divisionContextPath = "list/division?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";
        String districtContextPath = "list/district?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";
        String upazilaContextPath = "list/upazila?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";
        String paurasavaContextPath = "list/paurasava?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";
        String unionContextPath = "list/union?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";
        String wardContextPath = "list/ward?offset=0&limit=100&updatedSince=2014-11-11%2012:00:00";

        when(lrWebClient.get(divisionContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});
        when(lrWebClient.get(districtContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});
        when(lrWebClient.get(upazilaContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});
        when(lrWebClient.get(paurasavaContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});
        when(lrWebClient.get(unionContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});
        when(lrWebClient.get(wardContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class)).thenReturn(new LRAddressHierarchyEntry[]{});

        LocationPull locationPull = new LocationPull(propertiesReader, lrWebClient, addressHierarchyService, scheduledTaskHistory, addressHierarchyEntryMapper, addressHierarchyEntryTranslationRepository);
        locationPull.synchronize();

        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_DIVISIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_DISTRICTS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_UPAZILAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_PAURASAVAS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_UNIONS_LEVEL_FEED_URI);
        verify(scheduledTaskHistory, times(1)).getFeedUriForLastReadEntryByFeedUri(LR_WARDS_LEVEL_FEED_URI);
        //verify(propertiesReader, times(12)).getLrProperties();

        verify(lrWebClient).get(divisionContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(districtContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(upazilaContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(paurasavaContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(unionContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
        verify(lrWebClient).get(wardContextPath.replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE), LRAddressHierarchyEntry[].class);
    }

    public LRAddressHierarchyEntry[] getAddressHierarchyEntries(String responseFileName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        URL resource = URLClassLoader.getSystemResource("LRResponse/" + responseFileName + ".json");
        final String response = FileUtils.readFileToString(new File(resource.getPath()));
        return mapper.readValue(response, LRAddressHierarchyEntry[].class);
    }
}