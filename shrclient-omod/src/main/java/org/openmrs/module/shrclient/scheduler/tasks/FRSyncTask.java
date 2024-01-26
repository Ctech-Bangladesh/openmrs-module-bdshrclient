package org.openmrs.module.shrclient.scheduler.tasks;

import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.utils.OMRSLocationService;
import org.openmrs.module.shrclient.handlers.ClientRegistry;
import org.openmrs.module.shrclient.handlers.FacilityPull;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.mapper.LocationMapper;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;
import org.openmrs.scheduler.tasks.AbstractTask;

public class FRSyncTask extends AbstractTask {
    @Override
    public void execute() {
        PropertiesReader propertiesReader;
        RestClient frClient;
        try {
            propertiesReader = PlatformUtil.getPropertiesReader();
            IdentityStore identityStore = PlatformUtil.getIdentityStore();
            frClient = new ClientRegistry(propertiesReader, identityStore).getFRClient();

            new FacilityPull(propertiesReader, frClient,
                    Context.getService(LocationService.class),
                    PlatformUtil.getRegisteredComponent(ScheduledTaskHistory.class),
                    PlatformUtil.getIdMappingsRepository(),
                    new LocationMapper(),
                    PlatformUtil.getFacilityCatchmentRepository(),
                    PlatformUtil.getRegisteredComponent(OMRSLocationService.class)).synchronize();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }
}
