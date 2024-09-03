package org.openmrs.module.shrclient.util;

import org.openmrs.module.addresshierarchy.AddressField;

import java.util.HashMap;

import static org.openmrs.module.addresshierarchy.AddressField.*;

public enum AddressLevel {
    Division(STATE_PROVINCE, 1),
    Zilla(COUNTY_DISTRICT, 2),
    CityVillage(CITY_VILLAGE, 3),
    Upazilla(ADDRESS_5, 4),
    Paurasava(ADDRESS_4, 5),
    UnionOrWard(ADDRESS_3, 6),
    RuralWard(ADDRESS_2, 7),
    AddressLine(ADDRESS_1, 8);

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
        LOCATION_LEVELS.put("city-corporation", CityVillage);
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
