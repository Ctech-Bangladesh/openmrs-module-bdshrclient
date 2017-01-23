package org.openmrs.module.shrclient.scheduler.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.handlers.LocationPull;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.mapper.AddressHierarchyEntryMapper;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;
import org.openmrs.scheduler.tasks.AbstractTask;

public class LRSyncTask extends AbstractTask {

    @Override
    public void execute() {
        PropertiesReader propertiesReader;
        RestClient lrClient;
        AddressHierarchyEntryTranslationRepository entryTranslationRepository;
        try {
            propertiesReader = PlatformUtil.getPropertiesReader();
            IdentityStore identityStore = PlatformUtil.getIdentityStore();
            lrClient = new ClientRegistry(propertiesReader, identityStore).getLRClient();
            entryTranslationRepository = PlatformUtil.getAddressHierarchyEntryTranslationRepository();

            new LocationPull(propertiesReader, lrClient, Context.getService(AddressHierarchyService.class),
                    PlatformUtil.getRegisteredComponent(ScheduledTaskHistory.class), new AddressHierarchyEntryMapper(), entryTranslationRepository).synchronize();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }
}
