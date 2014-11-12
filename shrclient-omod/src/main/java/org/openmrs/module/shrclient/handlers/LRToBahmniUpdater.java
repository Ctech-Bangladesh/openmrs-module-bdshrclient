package org.openmrs.module.shrclient.handlers;

import org.apache.log4j.Logger;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.mapper.AddressHierarchyEntryMapper;
import org.openmrs.module.shrclient.mci.api.model.LRAddressHierarchyEntry;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.SchedulerTaskConfigQueryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LRToBahmniUpdater {
    private final Logger logger = Logger.getLogger(LRToBahmniUpdater.class);

    private static final String ENCODED_SINGLE_SPACE = "%20";
    private static final String SINGLE_SPACE = " ";

    private static final int EMPTY = 0;
    private static final int INITIAL_OFFSET = 0;
    private static final int DEFAULT_LIMIT = 100;
    private static final String EXTRA_FILTER_PATTERN_WITHOUT_UPDATED_SINCE = "?offset=%d&limit=%d";
    private static final String EXTRA_FILTER_PATTERN_WITH_UPDATED_SINCE = "?offset=%d&limit=%d&updatedSince=%s";
    private static final String LR_SYNC_TASK = "LR Sync Task";

    private AddressHierarchyEntryMapper addressHierarchyEntryMapper;
    private AddressHierarchyService addressHierarchyService;

    public LRToBahmniUpdater() {
        this.addressHierarchyEntryMapper = new AddressHierarchyEntryMapper();
        this.addressHierarchyService = Context.getService(AddressHierarchyService.class);
        Context.getService(Location)

    }

    public void update() {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        RestClient lrWebClient = propertiesReader.getLrWebClient();

        String lastExecutionDateAndTime = new SchedulerTaskConfigQueryUtil().getLastExecutionDateAndTime(LR_SYNC_TASK);

        List<LRAddressHierarchyEntry> lrAddressHierarchyEntriesForDivision = getLrAddressHierarchyEntryList(propertiesReader, "lr.divisions", lrWebClient, lastExecutionDateAndTime);
        List<LRAddressHierarchyEntry> lrAddressHierarchyEntriesForDistrict = getLrAddressHierarchyEntryList(propertiesReader, "lr.districts", lrWebClient, lastExecutionDateAndTime);
        List<LRAddressHierarchyEntry> lrAddressHierarchyEntriesForUpazila = getLrAddressHierarchyEntryList(propertiesReader, "lr.upazilas", lrWebClient, lastExecutionDateAndTime);
        List<LRAddressHierarchyEntry> lrAddressHierarchyEntriesForPaurasavas = getLrAddressHierarchyEntryList(propertiesReader, "lr.paurasavas", lrWebClient, lastExecutionDateAndTime);
        List<LRAddressHierarchyEntry> lrAddressHierarchyEntriesForUnions = getLrAddressHierarchyEntryList(propertiesReader, "lr.unions", lrWebClient, lastExecutionDateAndTime);


        saveOrUpdateAddressHierarchyEntries(lrAddressHierarchyEntriesForDivision);
        saveOrUpdateAddressHierarchyEntries(lrAddressHierarchyEntriesForDistrict);
        saveOrUpdateAddressHierarchyEntries(lrAddressHierarchyEntriesForUpazila);
        saveOrUpdateAddressHierarchyEntries(lrAddressHierarchyEntriesForPaurasavas);
        saveOrUpdateAddressHierarchyEntries(lrAddressHierarchyEntriesForUnions);

    }

    private void saveOrUpdateAddressHierarchyEntries(List<LRAddressHierarchyEntry> lrAddressHierarchyEntries) {
        for (LRAddressHierarchyEntry lrAddressHierarchyEntry : lrAddressHierarchyEntries) {
            AddressHierarchyEntry addressHierarchyEntry = addressHierarchyService.getAddressHierarchyEntryByUserGenId(lrAddressHierarchyEntry.getFullLocationCode());
            addressHierarchyEntry = addressHierarchyEntryMapper.map(addressHierarchyEntry, lrAddressHierarchyEntry, addressHierarchyService);
            try {
                if (addressHierarchyEntry.getId() == null)
                    logger.info("Saving Address Hierarchy Entry to Local DB : \n" + addressHierarchyEntry.toString());
                else
                    logger.info("Updating Address Hierarchy Entry to Local Db : " + addressHierarchyEntry.toString());

                addressHierarchyService.saveAddressHierarchyEntry(addressHierarchyEntry);
            } catch (Exception e) {
                logger.error("Error during Save Or Update to Local Db : " + e.toString());
            }
        }
    }

    private List<LRAddressHierarchyEntry> getLrAddressHierarchyEntryList(PropertiesReader propertiesReader, String hierarchy, RestClient lrWebClient, String lastExecutionDateAndTime) {
        List<LRAddressHierarchyEntry> lrAddressHierarchyEntries = new ArrayList<>();
        List<LRAddressHierarchyEntry> lastRetrievedPartOfList;
        int offset = INITIAL_OFFSET;

        do {
            lastRetrievedPartOfList = Arrays.asList(lrWebClient.get(getUrl(propertiesReader, hierarchy, offset, DEFAULT_LIMIT, lastExecutionDateAndTime), LRAddressHierarchyEntry[].class));
            offset += lastRetrievedPartOfList.size();
            lrAddressHierarchyEntries.addAll(lastRetrievedPartOfList);
        } while (lastRetrievedPartOfList.size() != EMPTY);
        return lrAddressHierarchyEntries;
    }

    private String getUrl(PropertiesReader propertiesReader, String hierarchy, int offset, int limit, String lastExecutionDateAndTime) {
        return propertiesReader.getLrProperties().getProperty(hierarchy) + getExtraFilters(offset, limit, lastExecutionDateAndTime);
    }

    private String getExtraFilters(int offset, int limit, String lastRanDateAndTime) {
        if (lastRanDateAndTime == null)
            return String.format(EXTRA_FILTER_PATTERN_WITHOUT_UPDATED_SINCE, offset, limit);
        else
            return String.format(EXTRA_FILTER_PATTERN_WITH_UPDATED_SINCE, offset, limit, lastRanDateAndTime).replace(SINGLE_SPACE, ENCODED_SINGLE_SPACE);
    }
}
