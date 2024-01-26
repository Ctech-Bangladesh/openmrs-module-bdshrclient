package org.openmrs.module.fhir.utils;


import org.apache.commons.beanutils.BeanToPropertyValueTransformer;
import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static org.openmrs.module.fhir.MRSProperties.*;

@Component
public class OMRSLocationService {

    @Autowired
    private LocationService locationService;

    @Autowired
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Autowired
    private IdMappingRepository idMappingRepository;

    public boolean isLocationHIEFacility(Location location) {
        Integer hieLocationTagId = getHIEFacilityLocationTag();
        if (location != null && location.getTags() != null && hieLocationTagId != null) {
            Collection locationTagIds = CollectionUtils.collect(location.getTags(), new BeanToPropertyValueTransformer("locationTagId"));
            return locationTagIds.contains(hieLocationTagId);
        }
        return false;
    }

    public String getLocationHIEIdentifier(Location location) {
        IdMapping locationMapping = idMappingRepository.findByInternalId(location.getUuid(), IdMappingType.FACILITY);
        return locationMapping != null ? locationMapping.getExternalId() : null;
    }

    public Integer getHIEFacilityLocationTag() {
        String hieFacilityLocationTag = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID);
        return hieFacilityLocationTag != null ? Integer.parseInt(hieFacilityLocationTag) : null;
    }

    public boolean isLoginLocation(Location location) {
        Integer loginLocationTagId = getLoginLocationTagId();
        if (location != null && location.getTags() != null && loginLocationTagId != null) {
            Collection locationTagIds = CollectionUtils.collect(location.getTags(), new BeanToPropertyValueTransformer("locationTagId"));
            return locationTagIds.contains(loginLocationTagId);
        }
        return false;
    }

    public Integer getLoginLocationTagId() {
        String loginLocationTagIdPropertyValue = globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_LOGIN_LOCATION_TAG_ID);
        return loginLocationTagIdPropertyValue != null ? Integer.parseInt(loginLocationTagIdPropertyValue) : getLocationTagIdByName();
    }

    public Integer getLocationTagIdByName() {
        List<LocationTag> locationTags = locationService.getLocationTags(MRS_LOGIN_LOCATION_TAG_NAME);
        for (LocationTag locationTag : locationTags) {
            if (locationTag.getName().equals(MRS_LOGIN_LOCATION_TAG_NAME)) {
                return locationTag.getLocationTagId();
            }
        }
        return null;
    }
}
