package org.openmrs.module.shrclient.scheduler.tasks;

import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.handlers.ProviderPull;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.mapper.ProviderMapper;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;
import org.openmrs.scheduler.tasks.AbstractTask;

import java.io.IOException;

public class PRSyncTask extends AbstractTask {
    @Override
    public void execute() {
        PropertiesReader propertiesReader;
        RestClient prClient;
        try {
            propertiesReader = PlatformUtil.getPropertiesReader();
            IdentityStore identityStore = PlatformUtil.getIdentityStore();
            prClient = new ClientRegistry(propertiesReader, identityStore).getPRClient();
            new ProviderPull(propertiesReader,
                    prClient,
                    PlatformUtil.getRegisteredComponent(ScheduledTaskHistory.class),
                    PlatformUtil.getRegisteredComponent(ProviderMapper.class)
            ).synchronize();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

    }
}
