package org.openmrs.module.shrclient.scheduler.tasks;

import org.apache.log4j.Logger;
import org.openmrs.scheduler.tasks.AbstractTask;

public class CatchmentPatientDownloadTask extends AbstractTask {
    private static final Logger log = Logger.getLogger(CatchmentPatientDownloadTask.class);

    @Override
    public void execute() {
//        Moved to encounter sync task.
//        TODO: Patient pull to have its own task. And avoid race conditions. 
    }
}
