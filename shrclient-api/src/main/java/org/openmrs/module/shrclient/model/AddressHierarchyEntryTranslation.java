package org.openmrs.module.shrclient.model;

public class AddressHierarchyEntryTranslation {
    private int id;
    private String localName;

    public AddressHierarchyEntryTranslation(int entryId, String localName) {
        this.id = entryId;
        this.localName = localName;
    }

    public int getId() {
        return id;
    }

    public String getLocalName() {
        return localName;
    }
}
