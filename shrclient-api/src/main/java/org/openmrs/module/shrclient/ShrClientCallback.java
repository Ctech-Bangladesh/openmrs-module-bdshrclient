package org.openmrs.module.shrclient;

import org.openmrs.Encounter;

public interface ShrClientCallback {

    Encounter saveEncounter(Encounter encounter);
}
