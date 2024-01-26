package org.openmrs.module.fhir.mapper.model;

import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.BaseResource;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.module.shrclient.util.StringUtil;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.lang.reflect.Type;
import java.util.HashMap;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;

public class EntityReference {
    public static final String REFERENCE_ID = "id";
    public static final String HEALTH_ID_REFERENCE = "healthId";
    public static final String ENCOUNTER_ID_REFERENCE = "encounterId";
    public static final String REFERENCE_RESOURCE_NAME = "resourceName";

    static DefaultedMap<Type, EntityReference> referenceMap;

    static {
        referenceMap = new DefaultedMap<>(new EntityReference());
        referenceMap.put(Patient.class, new PatientReference());
        referenceMap.put(Encounter.class, new EncounterReference());
        referenceMap.put(Location.class, new FacilityReference());
        referenceMap.put(Provider.class, new ProviderReference());
        referenceMap.put(BaseResource.class, new FHIResourceReference());
    }

    public String build(Type openmrsType, SystemProperties systemProperties, String id) {
        return referenceMap.get(openmrsType).create(id, systemProperties);
    }

    public String build(Type openmrsType, SystemProperties systemProperties, HashMap<String, String> ids) {
        return referenceMap.get(openmrsType).create(ids, systemProperties);
    }

    public String parse(Type type, String url) {
        return referenceMap.get(type).parseUrl(url);
    }

    public EntityReference getReference(Type type) {
        return referenceMap.get(type);
    }

    protected String parseUrl(String url) {
        return url;
    }

    protected String create(String id, SystemProperties systemProperties) {
        return "urn:uuid:" + id;
    }

    protected String create(HashMap<String, String> ids, SystemProperties systemProperties) {
        if (!ids.isEmpty()) return null;
        return create(ids.get(REFERENCE_ID), systemProperties);
    }

    private static class PatientReference extends EntityReference {

        @Override
        public String create(String id, SystemProperties systemProperties) {
            return StringUtil.ensureSuffix(systemProperties.getMciPatientUrl(), "/") + id;
        }

        @Override
        protected String parseUrl(String patientUrl) {
            return substringAfterLast(StringUtil.removeSuffix(patientUrl, "/"), "/");
        }
    }

    private static class EncounterReference extends EntityReference {
        @Override
        protected String create(String id, SystemProperties systemProperties) {
            throw new RuntimeException("Cannot create Encounter Url");
        }

        @Override
        protected String create(HashMap<String, String> ids, SystemProperties systemProperties) {
            String healthId = ids.get(HEALTH_ID_REFERENCE);
            String shrEncounterId = ids.get(REFERENCE_ID);
            String shrEncounterRefUrl = systemProperties.getShrEncounterUrl();
            return StringUtil.ensureSuffix(String.format(shrEncounterRefUrl, healthId), "/") + shrEncounterId;
        }

        @Override
        protected String parseUrl(String encounterUrl) {
            return StringUtils.substringAfterLast(StringUtil.removeSuffix(StringUtils.substringBefore(encounterUrl, "#"), "/"), "/");
        }
    }

    private static class FacilityReference extends EntityReference {
        @Override
        protected String create(String id, SystemProperties systemProperties) {
            return String.format("%s/%s.json",
                    StringUtil.removeSuffix(systemProperties.getFacilityResourcePath(), "/"), id);
        }

        @Override
        protected String parseUrl(String facilityUrl) {
            String s = StringUtils.substringAfterLast(StringUtil.removeSuffix(facilityUrl, "/"), "/");
            return StringUtils.substringBefore(s, ".json");
        }
    }

    private static class ProviderReference extends EntityReference {
        @Override
        protected String create(String identifier, SystemProperties systemProperties) {
            return String.format("%s/%s.json",
                    StringUtil.removeSuffix(systemProperties.getProviderResourcePath(), "/"), identifier);
        }

        @Override
        protected String parseUrl(String providerUrl) {
            String s = StringUtils.substringAfterLast(StringUtil.removeSuffix(providerUrl, "/"), "/");
            return StringUtils.substringBefore(s, ".json");
        }
    }

    private static class FHIResourceReference extends EntityReference {
        @Override
        protected String create(String id, SystemProperties systemProperties) {
            throw new RuntimeException("Cannot create Resource Url");
        }

        @Override
        protected String create(final HashMap<String, String> ids, final SystemProperties systemProperties) {
            HashMap<String, String> encounterReferenceHashMap = new HashMap<String, String>() {{
                put(HEALTH_ID_REFERENCE, ids.get(HEALTH_ID_REFERENCE));
                put(REFERENCE_ID, ids.get(ENCOUNTER_ID_REFERENCE));
            }};
            String encounterUrl = new EncounterReference().create(encounterReferenceHashMap, systemProperties);
            return String.format("%s#%s/%s", StringUtil.removeSuffix(encounterUrl, "/"), ids.get(REFERENCE_RESOURCE_NAME), ids.get(REFERENCE_ID));
        }

        @Override
        protected String parseUrl(String resourceUrl) {
            String resourceId = StringUtils.substringAfterLast(resourceUrl, "#");
            if (resourceId == null) return null;
            return StringUtils.substringAfterLast(StringUtil.removeSuffix(resourceId, "/"), "/");
        }
    }
}
