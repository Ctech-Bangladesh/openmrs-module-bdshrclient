package org.openmrs.module.shrclient.scheduler.tasks;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.handlers.EncounterPull;
import org.openmrs.module.shrclient.handlers.PatientPull;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.util.PlatformUtil;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.scheduler.tasks.AbstractTask;

public class CatchmentDownloadTask extends AbstractTask {
    private static final Logger log = Logger.getLogger(CatchmentDownloadTask.class);

    @Override
    public void execute() {
        PropertiesReader propertiesReader = PlatformUtil.getPropertiesReader();
        IdentityStore identityStore = PlatformUtil.getIdentityStore();
        new PatientPull(propertiesReader, identityStore).download();
        new PatientPull(propertiesReader, identityStore).retry();
        new EncounterPull(propertiesReader, identityStore).download();
        new EncounterPull(propertiesReader, identityStore).retry();
    }
}
