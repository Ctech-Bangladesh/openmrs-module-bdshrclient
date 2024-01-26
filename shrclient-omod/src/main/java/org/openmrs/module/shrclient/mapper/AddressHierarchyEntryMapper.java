package org.openmrs.module.shrclient.mapper;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.model.LRAddressHierarchyEntry;
import org.openmrs.module.shrclient.util.AddressHelper;

public class AddressHierarchyEntryMapper {

    public static final int SHORT_LOCATION_ID_LENGTH = 2;
    public static final int BEGIN_INDEX = 0;
    private final AddressHelper addressHelper;

    public AddressHierarchyEntryMapper() {
        this.addressHelper = new AddressHelper();
    }

    public AddressHierarchyEntryMapper(AddressHelper addressHelper) {
        this.addressHelper = addressHelper;
    }

    public AddressHierarchyEntry map(AddressHierarchyEntry addressHierarchyEntry,
                                     LRAddressHierarchyEntry lrAddressHierarchyEntry,
                                     AddressHierarchyService addressHierarchyService) {
        addressHierarchyEntry = addressHierarchyEntry == null ? new AddressHierarchyEntry() : addressHierarchyEntry;
        String parentUserGeneratedId = getParentUserGeneratedId(lrAddressHierarchyEntry.getFullLocationCode());
        AddressHierarchyEntry parentAddressHierarchyEntry = getParent(parentUserGeneratedId, addressHierarchyService);
        addressHierarchyEntry.setName(lrAddressHierarchyEntry.getLocationName());
        addressHierarchyEntry.setLevel(addressHelper.getAddressHierarchyLevelFromLrLevel(lrAddressHierarchyEntry.getLocationLevelName()));
        addressHierarchyEntry.setParent(parentAddressHierarchyEntry);
        addressHierarchyEntry.setUserGeneratedId(lrAddressHierarchyEntry.getFullLocationCode());
        return addressHierarchyEntry;
    }

    public AddressHierarchyEntry getParent(String parentUserGeneratedId,
                                           AddressHierarchyService addressHierarchyService) {
        return addressHierarchyService.getAddressHierarchyEntryByUserGenId(parentUserGeneratedId);
    }

    public String getParentUserGeneratedId(String fullLocationCode) {
        String parentUserGeneratedId = fullLocationCode.substring(BEGIN_INDEX, fullLocationCode.length() - SHORT_LOCATION_ID_LENGTH);
        return StringUtils.isEmpty(parentUserGeneratedId) ? null : parentUserGeneratedId;
    }

}
