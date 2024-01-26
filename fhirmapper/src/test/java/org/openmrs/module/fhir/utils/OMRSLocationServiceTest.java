package org.openmrs.module.fhir.utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.api.LocationService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;

import java.util.Date;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID;
import static org.openmrs.module.fhir.MRSProperties.GLOBAL_PROPERTY_SHR_LOGIN_LOCATION_TAG_ID;
import static org.openmrs.module.fhir.MRSProperties.MRS_LOGIN_LOCATION_TAG_NAME;

public class OMRSLocationServiceTest {

    @Mock
    private GlobalPropertyLookUpService globalPropertyLookUpService;

    @Mock
    private IdMappingRepository idMappingRepository;
    
    @Mock
    private LocationService locationService;

    @InjectMocks
    OMRSLocationService omrsLocationService;

    @Before
    public void setUp(){
        initMocks(this);
    }

    @Test
    public void shouldMarkALocationAsHIEFacilityIfTaggedAsHIEFacility() throws Exception {
        Location hieLocation = new Location();
        hieLocation.setTags(new HashSet<LocationTag>() {{
            LocationTag HIEFacilityTag = new LocationTag();
            HIEFacilityTag.setId(10);
            add(HIEFacilityTag);
        }});
        when(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID)).thenReturn("10");

        assertTrue(omrsLocationService.isLocationHIEFacility(hieLocation));
    }

    @Test
    public void shouldNotMarkLocationAsHIEFacilityIfNotTaggedAsHIEFacility() throws Exception {
        Location someOtherLocation = new Location();
        when(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID)).thenReturn("10");

        assertFalse(omrsLocationService.isLocationHIEFacility(someOtherLocation));
    }

    @Test
    public void shouldNotMarkAnInvalidLocationAsHIEFacility() throws Exception {
        assertFalse(omrsLocationService.isLocationHIEFacility(null));
    }

    @Test
    public void shouldNotMarkALocationAsHIEFacilityIfNoGlobalPropertyIsConfiguredAsHIELocationTagId() throws Exception {
        when(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_HIE_FACILITY_LOCATION_TAG_ID)).thenReturn(null);

        assertFalse(omrsLocationService.isLocationHIEFacility(new Location()));
    }

    @Test
    public void shouldReturnHIEIdentifierOfALocation() throws Exception {
        String uuid = "location uuid";
        when(idMappingRepository.findByInternalId(uuid, IdMappingType.FACILITY)).thenReturn(new IdMapping(uuid, "hie id", "", "", new Date()));

        Location hieLocation = new Location();
        hieLocation.setUuid(uuid);
        assertEquals("hie id",omrsLocationService.getLocationHIEIdentifier(hieLocation));

    }

    @Test
    public void shouldReturnNullIfALocationHasNoHIEIdentifierMapping() throws Exception {
        String uuid = "location uuid";
        when(idMappingRepository.findByInternalId(uuid, IdMappingType.FACILITY)).thenReturn(null);

        Location hieLocation = new Location();
        hieLocation.setUuid(uuid);
        assertNull(omrsLocationService.getLocationHIEIdentifier(hieLocation));

    }

    @Test
    public void shouldMarkALocationAsLoginLocationIfTaggedAsLoginLocation() throws Exception {
        Location loginLocation = new Location();
        loginLocation.setTags(new HashSet<LocationTag>() {{
            LocationTag loginLocationTag = new LocationTag();
            loginLocationTag.setId(1);
            add(loginLocationTag);
        }});
        when(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_LOGIN_LOCATION_TAG_ID)).thenReturn("1");

        assertTrue(omrsLocationService.isLoginLocation(loginLocation));
    }

    @Test
    public void shouldMatchLoginLocationTagWithNameIfNotSpecifiedInProperties() throws Exception {
        Location loginLocation = new Location();
        
        LocationTag loginLocationTag = new LocationTag();
        loginLocationTag.setId(1);
        loginLocationTag.setName(MRS_LOGIN_LOCATION_TAG_NAME);
        
        HashSet<LocationTag> tags = new HashSet<>();
        tags.add(loginLocationTag);
        loginLocation.setTags(tags);
        
        when(globalPropertyLookUpService.getGlobalPropertyValue(GLOBAL_PROPERTY_SHR_LOGIN_LOCATION_TAG_ID)).thenReturn(null);
        when(locationService.getLocationTags(MRS_LOGIN_LOCATION_TAG_NAME)).thenReturn(asList(loginLocationTag));

        assertTrue(omrsLocationService.isLoginLocation(loginLocation));
    }
}