package org.openmrs.module.shrclient.handlers;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.mapper.AddressHierarchyEntryMapper;
import org.openmrs.module.shrclient.model.LRAddressHierarchyEntry;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.openmrs.module.shrclient.util.URLParser.parseURL;

//purpose: this class represents a location repository and provides synchronization to local OpenMRS server
public class LocationPull {
    private final Logger logger = Logger.getLogger(LocationPull.class);

    public static final String LR_DIVISIONS_LEVEL_FEED_URI = "urn://lr/divisions";
    public static final String LR_DISTRICTS_LEVEL_FEED_URI = "urn://lr/districts";
    public static final String LR_UPAZILAS_LEVEL_FEED_URI = "urn://lr/upazilas";
    public static final String LR_PAURASAVAS_LEVEL_FEED_URI = "urn://lr/paurasavas";
    public static final String LR_UNIONS_LEVEL_FEED_URI = "urn://lr/unions";
    public static final String LR_WARDS_LEVEL_FEED_URI = "urn://lr/wards";
    public static final String LR_DIVISIONS = "lr.divisions";
    public static final String LR_DISTRICTS = "lr.districts";
    public static final String LR_UPAZILAS = "lr.upazilas";
    public static final String LR_PAURASAVAS = "lr.paurasavas";
    public static final String LR_UNIONS = "lr.unions";
    public static final String LR_WARDS = "lr.wards";
    public static final String ENCODED_SINGLE_SPACE = "%20";

    public static final int INTIAL_OFFSET = 0;
    public static final String OFFSET = "offset";
    public static final String UPDATED_SINCE = "updatedSince";
    public static final String SINGLE_SPACE = " ";
    private static final int DEFAULT_LIMIT = 100;
    private static final String EXTRA_FILTER_PATTERN = "?offset=%s&limit=%s&updatedSince=%s";
    private static final int MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED = 1000;
    private static final String INITIAL_DATETIME = "0000-00-00 00:00:00";

    private ScheduledTaskHistory scheduledTaskHistory;
    private AddressHierarchyEntryMapper addressHierarchyEntryMapper;
    private AddressHierarchyService addressHierarchyService;
    private RestClient lrWebClient;
    private PropertiesReader propertiesReader;
    private List<String> failedDuringSaveOrUpdateOperation;
    private int noOfEntriesSynchronizedSoFar;

    public LocationPull(PropertiesReader propertiesReader, RestClient lrWebClient, AddressHierarchyService addressHierarchyService,
                        ScheduledTaskHistory scheduledTaskHistory, AddressHierarchyEntryMapper addressHierarchyEntryMapper) {
        this.lrWebClient = lrWebClient;
        this.propertiesReader = propertiesReader;
        this.scheduledTaskHistory = scheduledTaskHistory;
        this.addressHierarchyEntryMapper = addressHierarchyEntryMapper;
        this.addressHierarchyService = addressHierarchyService;
        this.failedDuringSaveOrUpdateOperation = new ArrayList<>();
    }

    public void synchronize() throws IOException {
        noOfEntriesSynchronizedSoFar = 0;

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForDivisions = synchronizeUpdatesByLevel(LR_DIVISIONS, LR_DIVISIONS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForDivisions.size() + " entries updated");

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForDistricts = synchronizeUpdatesByLevel(LR_DISTRICTS, LR_DISTRICTS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForDistricts.size() + " entries updated");

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForUpazilas = synchronizeUpdatesByLevel(LR_UPAZILAS, LR_UPAZILAS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForUpazilas.size() + " entries updated");

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForPaurasavas = synchronizeUpdatesByLevel(LR_PAURASAVAS, LR_PAURASAVAS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForPaurasavas.size() + " entries updated");

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForUnions = synchronizeUpdatesByLevel(LR_UNIONS, LR_UNIONS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForUnions.size() + " entries updated");

        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntriesForWards = synchronizeUpdatesByLevel(LR_WARDS, LR_WARDS_LEVEL_FEED_URI);
        logger.info(synchronizedAddressHierarchyEntriesForWards.size() + " entries updated");
    }

    private List<LRAddressHierarchyEntry> synchronizeUpdatesByLevel(String levelName, String feedUri) throws IOException {
        String baseContextPath = propertiesReader.getLrProperties().getProperty(levelName);
        List<LRAddressHierarchyEntry> synchronizedAddressHierarchyEntries = new ArrayList<>();
        List<LRAddressHierarchyEntry> lastRetrievedPartOfList;

        if (noOfEntriesSynchronizedSoFar >= MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED) {
            return synchronizedAddressHierarchyEntries;
        }

        String feedUriForLastReadEntry = scheduledTaskHistory.getFeedUriForLastReadEntryByFeedUri(feedUri);
        int offset;
        String updatedSince;

        if (StringUtils.isBlank(feedUriForLastReadEntry)) {
            offset = INTIAL_OFFSET;
            updatedSince = INITIAL_DATETIME;
        } else {
            Map<String, String> parameters = parseURL(new URL(feedUriForLastReadEntry));
            offset = Integer.parseInt(parameters.get(OFFSET));
            updatedSince = parameters.get(UPDATED_SINCE);
        }
        String baseUrl = getBaseUrl(propertiesReader.getLrProperties());
        String completeContextPath;
        do {
            completeContextPath = buildCompleteContextPath(baseContextPath, offset, updatedSince);
            lastRetrievedPartOfList = getNextChunkOfUpdatesFromLR(completeContextPath);
            if (lastRetrievedPartOfList != null) {
                saveOrUpdateAddressHierarchyEntries(lastRetrievedPartOfList);
                synchronizedAddressHierarchyEntries.addAll(lastRetrievedPartOfList);
                offset += lastRetrievedPartOfList.size();
                noOfEntriesSynchronizedSoFar += lastRetrievedPartOfList.size();
            } else {
                logger.info(synchronizedAddressHierarchyEntries.size() + " entries synchronized");
                throw new RuntimeException("Failed to Synchronize updates from LR");
            }
        }
        while (lastRetrievedPartOfList != null && lastRetrievedPartOfList.size() == DEFAULT_LIMIT && noOfEntriesSynchronizedSoFar < MAX_NUMBER_OF_ENTRIES_TO_BE_SYNCHRONIZED);

        String nextCompleteContextPath;
        if (lastRetrievedPartOfList != null) {
            if (lastRetrievedPartOfList.size() == DEFAULT_LIMIT) {
                nextCompleteContextPath = buildCompleteContextPath(baseContextPath, offset, INITIAL_DATETIME);
                scheduledTaskHistory.setFeedUriForLastReadEntryByFeedUri(baseUrl + nextCompleteContextPath, feedUri);
            } else {
                nextCompleteContextPath = buildCompleteContextPath(baseContextPath, INTIAL_OFFSET, getCurrentDateAndTime());
                scheduledTaskHistory.setFeedUriForLastReadEntryByFeedUri(baseUrl + nextCompleteContextPath, feedUri);
            }

            if (!synchronizedAddressHierarchyEntries.isEmpty()) {
                LRAddressHierarchyEntry lastReadAddressHierarchyEntry = lastRetrievedPartOfList.get(lastRetrievedPartOfList.size() - 1);
                scheduledTaskHistory.setLastReadEntryId(lastReadAddressHierarchyEntry.getFullLocationCode(), feedUri);
            }
        }

        logger.info(synchronizedAddressHierarchyEntries.size() + " entries synchronized");
        logger.info(failedDuringSaveOrUpdateOperation.size() + " entries failed during synchronization");
        logger.info("Synchronization Failed for the following Facilities");
        logger.info(failedDuringSaveOrUpdateOperation.toString());

        return synchronizedAddressHierarchyEntries;
    }

    private String getBaseUrl(Properties properties) {
        return properties.getProperty("lr.scheme") + "://" + properties.getProperty("lr.host") + "/" + properties.getProperty("lr.context");
    }

    private List<LRAddressHierarchyEntry> getNextChunkOfUpdatesFromLR(String completeContextPath) {
        List<LRAddressHierarchyEntry> downloadedData = null;
        try {
            downloadedData = Arrays.asList(lrWebClient.get(completeContextPath, LRAddressHierarchyEntry[].class));
        } catch (Exception e) {
            logger.error("Error while downloading chunk of Updates from LR : " + e);
        }
        return downloadedData;
    }

    private String buildCompleteContextPath(String baseContextPath, int offset, String updatedSince) {
        return baseContextPath + getExtraFilters(offset, updatedSince);
    }

    private String getExtraFilters(int offset, String updatedSince) {
        return String.format(EXTRA_FILTER_PATTERN, offset, DEFAULT_LIMIT, updatedSince).replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE);
    }

    private void saveOrUpdateAddressHierarchyEntries(List<LRAddressHierarchyEntry> lrAddressHierarchyEntries) {
        for (LRAddressHierarchyEntry lrAddressHierarchyEntry : lrAddressHierarchyEntries) {
            AddressHierarchyEntry addressHierarchyEntry = addressHierarchyService.getAddressHierarchyEntryByUserGenId(lrAddressHierarchyEntry.getFullLocationCode());
            addressHierarchyEntry = addressHierarchyEntryMapper.map(addressHierarchyEntry, lrAddressHierarchyEntry, addressHierarchyService);
            try {
                if (addressHierarchyEntry.getId() == null) {
                    logger.info("Saving Address Hierarchy Entry to Local DB : \n" + addressHierarchyEntry.toString());
                } else {
                    logger.info("Updating Address Hierarchy Entry to Local Db : " + addressHierarchyEntry.toString());
                }
                addressHierarchyService.saveAddressHierarchyEntry(addressHierarchyEntry);
            } catch (Exception e) {
                logger.error("Error during Save Or Update to Local Db : " + e.toString());
                failedDuringSaveOrUpdateOperation.add(lrAddressHierarchyEntry.toString());
            }
        }
    }

    private String getCurrentDateAndTime() {
        return new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date());
    }
}
