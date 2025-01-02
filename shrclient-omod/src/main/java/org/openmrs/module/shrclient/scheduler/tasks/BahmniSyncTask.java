package org.openmrs.module.shrclient.scheduler.tasks;


import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.handlers.EncounterPush;
import org.openmrs.module.shrclient.handlers.PatientPush;
import org.openmrs.module.shrclient.util.PropertiesReader;

public class BahmniSyncTask extends AbstractBahmniSyncTask {
    private static final Logger log = Logger.getLogger(BahmniSyncTask.class);

    @Override
    protected void executeBahmniTask(PatientPush patientPush, EncounterPush encounterPush, PropertiesReader propertiesReader) {

        if (!isInternetAvailable()) {
            log.warn("Internet is not available. Skipping the SHR Patient Sync Task.");
            return;
        }

        /*
        * todo: for now this class processes new and failed events both. Failed events should be processed in BahmniSyncRetryTask.
        * */
        log.debug("SCHEDULED JOB : SHR Patient Sync Task");
        try {
          ///  getFeedClient(OPENMRS_PATIENT_FEED_URI, patientPush, propertiesReader.getMciMaxFailedEvent()).processEvents();
          //  getFeedClient(OPENMRS_PATIENT_FEED_URI, patientPush, propertiesReader.getMciMaxFailedEvent()).processFailedEvents();
            getFeedClient(OPENMRS_ENCOUNTER_FEED_URI, encounterPush, propertiesReader.getShrMaxFailedEvent()).processEvents();
            getFeedClient(OPENMRS_ENCOUNTER_FEED_URI, encounterPush, propertiesReader.getShrMaxFailedEvent()).processFailedEvents();
        } catch (URISyntaxException e) {
            log.error(e.getMessage());
        }
    }

    private boolean isInternetAvailable() {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return (responseCode == 200);
        } catch (Exception e) {
            log.error("HTTP-based internet check failed: " + e.getMessage());
            return false;
        }
    }

}
