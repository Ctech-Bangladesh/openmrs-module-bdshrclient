package org.openmrs.module.shrclient.handlers;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.fhir.utils.OMRSLocationService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.dao.FacilityCatchmentRepository;
import org.openmrs.module.shrclient.mapper.LocationMapper;
import org.openmrs.module.shrclient.model.FRLocationEntry;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;
import org.openmrs.module.shrclient.util.StringUtil;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.openmrs.module.shrclient.util.URLParser.parseURL;

public class FacilityPull {
    private final Logger logger = Logger.getLogger(FacilityPull.class);

    private static final int INITIAL_OFFSET = 0;
    private static final String OFFSET = "offset";
    private static final String UPDATED_SINCE = "updatedSince";
    private static final int DEFAULT_LIMIT = 100;
    private static final String EXTRA_FILTER_PATTERN = "?offset=%d&limit=%d&updatedSince=%s";
    private static final String ENCODED_SINGLE_SPACE = "%20";
    private static final String SINGLE_SPACE = " ";
    public static final String FR_FACILITY_LEVEL_FEED_URI = "urn://fr/facilities";
    public static final String FR_PATH_INFO = "fr.pathInfo";
    private static final int MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED = 1000;
    private static final String INITIAL_DATETIME = "0000-00-00 00:00:00";

    private final LocationService locationService;
    private final IdMappingRepository idMappingsRepository;
    private final LocationMapper locationMapper;
    private final FacilityCatchmentRepository facilityCatchmentRepository;
    private final ScheduledTaskHistory scheduledTaskHistory;
    private final PropertiesReader propertiesReader;
    private final RestClient frWebClient;
    private final LocationTag shrLocationTag;
    private final List<String> failedDuringSaveOrUpdateOperation;
    private int noOfEntriesSynchronizedSoFar = 0;


    public FacilityPull(PropertiesReader propertiesReader, RestClient frWebClient, LocationService locationService,
                        ScheduledTaskHistory scheduledTaskHistory, IdMappingRepository idMappingRepository,
                        LocationMapper locationMapper, FacilityCatchmentRepository facilityCatchmentRepository, OMRSLocationService omrsLocationService) {
        this.propertiesReader = propertiesReader;
        this.frWebClient = frWebClient;
        this.locationService = locationService;
        this.idMappingsRepository = idMappingRepository;
        this.scheduledTaskHistory = scheduledTaskHistory;
        this.locationMapper = locationMapper;
        this.facilityCatchmentRepository = facilityCatchmentRepository;
        this.shrLocationTag =  locationService.getLocationTag(omrsLocationService.getHIEFacilityLocationTag());
        this.failedDuringSaveOrUpdateOperation = new ArrayList<>();
    }

    public void synchronize() throws IOException {
        noOfEntriesSynchronizedSoFar = 0;
        List<FRLocationEntry> frLocationEntries = synchronizeUpdates();
        logger.info(frLocationEntries.size() + " entries updated");
    }

    private List<FRLocationEntry> synchronizeUpdates() throws IOException {
        String baseContextPath = propertiesReader.getFrProperties().getProperty(FR_PATH_INFO);
        List<FRLocationEntry> synchronizedLocationEntries = new ArrayList<>();
        List<FRLocationEntry> lastRetrievedPartOfList;

        if (noOfEntriesSynchronizedSoFar >= MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED) {
            return synchronizedLocationEntries;
        }

        String feedUriForLastReadEntry = scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(FR_FACILITY_LEVEL_FEED_URI);
        int offset;
        String updatedSince = null;

        if (StringUtils.isBlank(feedUriForLastReadEntry)) {
            offset = INITIAL_OFFSET;
            updatedSince = INITIAL_DATETIME;
        } else {
            Map<String, String> parameters = parseURL(new URL(feedUriForLastReadEntry));
            offset = Integer.parseInt(parameters.get(OFFSET));
            String lastUpdate = parameters.get(UPDATED_SINCE);
            if (!StringUtils.isBlank(lastUpdate)) {
                lastUpdate = lastUpdate.replace("%20", " ");
                updatedSince = lastUpdate;
            }
        }

        String facilityResourceRefPath = StringUtil.ensureSuffix(propertiesReader.getFrBaseUrl(), "/");
        String completeContextPath;
        do {
            completeContextPath = buildCompleteContextPath(baseContextPath, offset, updatedSince);
            lastRetrievedPartOfList = getNextChunkOfUpdatesFromFR(completeContextPath);
            if (lastRetrievedPartOfList != null) {
                saveOrUpdateFacilityEntries(lastRetrievedPartOfList);
                synchronizedLocationEntries.addAll(lastRetrievedPartOfList);
                offset += lastRetrievedPartOfList.size();
                noOfEntriesSynchronizedSoFar += lastRetrievedPartOfList.size();
            } else {
                logger.info(synchronizedLocationEntries.size() + " entries synchronized");
                throw new RuntimeException("Failed to Synchronize updates from FR");
            }
        }
        while (lastRetrievedPartOfList != null && lastRetrievedPartOfList.size() == DEFAULT_LIMIT && noOfEntriesSynchronizedSoFar < MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED);

        String nextCompleteContextPath;
        if (lastRetrievedPartOfList != null) {
            if (lastRetrievedPartOfList.size() == DEFAULT_LIMIT) {
                //ideally should take the last ProviderEntry.updatedAt (currently updatedAt is not mapped) from the newEntriesFromPr
                //and also should reset the offset accordingly
                nextCompleteContextPath = buildCompleteContextPath(baseContextPath, offset, updatedSince);
                scheduledTaskHistory.setFeedUriForLastReadEntryByFeedUri(facilityResourceRefPath + StringUtil.removePrefix(nextCompleteContextPath, "/"), FR_FACILITY_LEVEL_FEED_URI);
            } else {
                nextCompleteContextPath = buildCompleteContextPath(baseContextPath, INITIAL_OFFSET, getCurrentDateAndTime());
                scheduledTaskHistory.setFeedUriForLastReadEntryByFeedUri(facilityResourceRefPath + StringUtil.removePrefix(nextCompleteContextPath, "/"), FR_FACILITY_LEVEL_FEED_URI);
            }

            if (!synchronizedLocationEntries.isEmpty()) {
                FRLocationEntry frLocationEntry = lastRetrievedPartOfList.get(lastRetrievedPartOfList.size() - 1);
                scheduledTaskHistory.setLastReadEntryId(frLocationEntry.getId(), FR_FACILITY_LEVEL_FEED_URI);
            }
        }

        logger.info(synchronizedLocationEntries.size() + " entries synchronized");
        logger.info(failedDuringSaveOrUpdateOperation.size() + " entries failed during synchronization");
        logger.info("Synchronization Failed for the following Facilities");
        logger.info(failedDuringSaveOrUpdateOperation.toString());

        return synchronizedLocationEntries;
    }

    private List<FRLocationEntry> getNextChunkOfUpdatesFromFR(String completeContextPath) {
        List<FRLocationEntry> downloadedData = null;
        try {
            downloadedData = Arrays.asList(frWebClient.get(completeContextPath, FRLocationEntry[].class));
        } catch (Exception e) {
            logger.error("Error while downloading chunk of Updates from FR : " + e);
        }
        return downloadedData;
    }

    private Location createNewLocation(FRLocationEntry frLocationEntry) {
        logger.info("Creating new location: " + frLocationEntry.getName());
        Location location = null;
        try {
            location = locationMapper.create(frLocationEntry);
            location.addTag(shrLocationTag);
            location = locationService.saveLocation(location);

            facilityCatchmentRepository.saveMappings(location.getLocationId(), frLocationEntry.getProperties().getCatchments());

            String locationUrl = StringUtil.ensureSuffix(propertiesReader.getFrBaseUrl(), "/") + frLocationEntry.getId() + ".json";
            idMappingsRepository.saveOrUpdateIdMapping(new IdMapping(location.getUuid(), frLocationEntry.getId(),
                    IdMappingType.FACILITY, locationUrl, new Date()));

        } catch (Exception e) {
            logger.error("Error while creating a new Location : " + e);
            logger.info("Logging the failed event : " + frLocationEntry.toString());
            failedDuringSaveOrUpdateOperation.add(frLocationEntry.toString());
        }
        return location;
    }

    private Location updateExistingLocation(FRLocationEntry frLocationEntry, IdMapping idMapping) {
        logger.info("Updating existing location: " + frLocationEntry.getName());
        Location location = null;
        try {
            location = locationMapper.updateExisting(
                    locationService.getLocationByUuid(idMapping.getInternalId()), frLocationEntry);
            facilityCatchmentRepository.saveMappings(location.getLocationId(), frLocationEntry.getProperties().getCatchments());

        } catch (Exception e) {
            logger.error("Error while updating an old Location : " + e);
            logger.info("Logging the failed event : " + frLocationEntry.toString());
            failedDuringSaveOrUpdateOperation.add(frLocationEntry.toString());
        }
        return locationService.saveLocation(location);
    }

    private void saveOrUpdateFacilityEntries(List<FRLocationEntry> frLocationEntries) {
        for (FRLocationEntry frLocationEntry : frLocationEntries) {
            IdMapping facilityIdMapping = idMappingsRepository.findByExternalId(frLocationEntry.getId(), IdMappingType.FACILITY);
            if (facilityIdMapping != null)
                updateExistingLocation(frLocationEntry, facilityIdMapping);
            else {
                createNewLocation(frLocationEntry);
            }
        }
    }

    private String buildCompleteContextPath(String baseContextPath, int offset, String updatedSince) {
        return baseContextPath + getExtraFilters(offset, updatedSince);
    }

    private String getExtraFilters(int offset, String updatedSince) {
        return String.format(EXTRA_FILTER_PATTERN, offset, DEFAULT_LIMIT, updatedSince)
                .replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE);
    }

    private String getCurrentDateAndTime() {
        return DateUtil.toDateString(new Date(), DateUtil.SIMPLE_DATE_WITH_SECS_FORMAT);
    }
}
