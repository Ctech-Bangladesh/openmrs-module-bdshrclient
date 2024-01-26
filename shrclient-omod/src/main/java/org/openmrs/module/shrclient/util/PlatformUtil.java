package org.openmrs.module.shrclient.util;


import org.openmrs.api.context.Context;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.dao.FacilityCatchmentRepository;
import org.openmrs.module.shrclient.identity.IdentityStore;

import java.util.List;

public class PlatformUtil {
    public static <T> T getRegisteredComponent(Class<T> clazz) {
        List<T> registeredComponents = Context.getRegisteredComponents(clazz);
        if (!registeredComponents.isEmpty()) {
            return registeredComponents.get(0);
        }
        return null;
    }

    public static <T> T getRegisteredComponent(String beanName, Class<T> clazz) {
        return Context.getRegisteredComponent(beanName, clazz);
    }

    public static PropertiesReader getPropertiesReader() {
        return getRegisteredComponent(PropertiesReader.class);
    }

    public static AddressHierarchyEntryTranslationRepository getAddressHierarchyEntryTranslationRepository() {
        return getRegisteredComponent(AddressHierarchyEntryTranslationRepository.class);
    }

    public static IdMappingRepository getIdMappingsRepository() {
        return getRegisteredComponent(IdMappingRepository.class);
    }

    public static FacilityCatchmentRepository getFacilityCatchmentRepository() {
        return getRegisteredComponent(FacilityCatchmentRepository.class);
    }

    public static IdentityStore getIdentityStore() {
        return getRegisteredComponent(IdentityStore.class);
    }
}
