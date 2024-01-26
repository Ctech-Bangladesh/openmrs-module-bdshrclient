package org.openmrs.module.shrclient.util;

import org.openmrs.module.addresshierarchy.AddressField;

import java.util.HashMap;

import static org.openmrs.module.addresshierarchy.AddressField.*;

public enum AddressLevel {
    Division(STATE_PROVINCE, 1),
    Zilla(COUNTY_DISTRICT, 2),
    Upazilla(ADDRESS_5, 3),
    Paurasava(ADDRESS_4, 4),
    UnionOrWard(ADDRESS_3, 5),
    RuralWard(ADDRESS_2, 6),
    AddressLine(ADDRESS_1, 7);

    public static final HashMap<String, AddressLevel> LOCATION_LEVELS = new HashMap<>();
    private AddressField addressField;
    private final int levelNumber;

    static {
        LOCATION_LEVELS.put("division", Division);
        LOCATION_LEVELS.put("district", Zilla);
        LOCATION_LEVELS.put("upazila", Upazilla);
        LOCATION_LEVELS.put("paurasava", Paurasava);
        LOCATION_LEVELS.put("union", UnionOrWard);
        LOCATION_LEVELS.put("ward", RuralWard);
    }

    AddressLevel(AddressField addressField, int levelNumber) {
        this.addressField = addressField;
        this.levelNumber = levelNumber;
    }

    public AddressField getAddressField() {
        return addressField;
    }

    public int getLevelNumber() {
        return levelNumber;
    }
}
