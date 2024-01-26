package org.openmrs.module.shrclient.scheduler.tasks;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.handlers.EncounterPush;
import org.openmrs.module.shrclient.handlers.PatientPush;
import org.openmrs.module.shrclient.util.PropertiesReader;

import java.net.URISyntaxException;

public class BahmniSyncRetryTask extends AbstractBahmniSyncTask {
    private static final Logger log = Logger.getLogger(BahmniSyncRetryTask.class);

    @Override
    protected void executeBahmniTask(PatientPush patientPush, EncounterPush encounterPush, PropertiesReader propertiesReader) {
        /*
        * todo: for now BahmniSyncTask processes new and failed events both. Failed events should be processed here.
        * */
    }
}
