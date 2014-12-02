package org.openmrs.module.fhir.mapper.model;

import org.apache.commons.collections4.map.DefaultedMap;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.lang.reflect.Type;

public class EntityReference {

    static DefaultedMap <Type, EntityReference> referenceMap;
    static {
        referenceMap = new DefaultedMap<>(new EntityReference());
        referenceMap.put(Patient.class, new PatientReference());
        referenceMap.put(Encounter.class, new EncounterReference());
        referenceMap.put(Location.class, new FacilityReference());
    }

    public String build(Type type, SystemProperties systemProperties, String id) {
        return referenceMap.get(type).create(id, systemProperties);
    }

    protected String create(String id, SystemProperties systemProperties) {
        return "urn:" + id;
    }

    private static class PatientReference extends EntityReference {

        @Override
        public String create(String id, SystemProperties systemProperties) {
            return systemProperties.getMciPatientUrl() + id;
        }
    }

    private static class EncounterReference extends EntityReference {
        @Override
        protected String create(String id, SystemProperties systemProperties) {
            return "urn:" +  id;
        }
    }

    private static class FacilityReference extends EntityReference {
        @Override
        protected String create(String id, SystemProperties systemProperties) {
            return systemProperties.getFrBaseUrl() + String.format(systemProperties.getFacilityUrlFormat(), id);
        }
    }
}
