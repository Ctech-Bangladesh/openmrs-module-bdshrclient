package org.openmrs.module.shrclient.mapper;

import org.junit.Test;
import org.openmrs.Location;
import org.openmrs.module.shrclient.model.FRLocationEntry;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocationMapperTest {
    @Test
    public void shouldUpdateExisting() throws Exception {
        Location existingLocation = loadExistingLocation();
        assertFalse(existingLocation.getRetired());
        Location location = new LocationMapper().updateExisting(existingLocation, createLocationEntry());
        assertTrue(location.getRetired());
        assertEquals("bar (100001)", location.getName());
    }

    @Test
    public void shouldCreateNew() throws Exception {
        Location location = new LocationMapper().create(createLocationEntry());
        assertTrue(location.getRetired());
        assertEquals("bar (100001)", location.getName());
    }

    private FRLocationEntry createLocationEntry() {
        FRLocationEntry frLocationEntry = new FRLocationEntry();
        frLocationEntry.setId("100001");
        frLocationEntry.setName("bar");
        frLocationEntry.setActive("0");
        return frLocationEntry;
    }

    private Location loadExistingLocation() {
        Location location = new Location();
        location.setName("foo");
        location.setRetired(false);
        return location;
    }
}