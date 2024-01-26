package org.openmrs.module.shrclient.web.controller;


import org.apache.log4j.Logger;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.shrclient.service.MCIPatientLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping(value = "/mci")
public class MciPatientLookupController {
    private static final Logger log = Logger.getLogger(MciPatientLookupController.class);

    @Autowired
    private MCIPatientLookupService mciPatientLookupService;

    @RequestMapping(method = RequestMethod.GET, value = "/search")
    @ResponseBody
    public Object search(MciPatientSearchRequest request, HttpServletResponse response) throws IOException {
        try {
            return mciPatientLookupService.searchPatientInRegistry(request);
        } catch (APIAuthenticationException e) {
            log.info("Not authorized to access national registry");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return null;
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/download")
    @ResponseBody
    public Object download(MciPatientSearchRequest request, HttpServletResponse response) throws IOException {
        try {
            return mciPatientLookupService.downloadPatient(request);
        } catch (APIAuthenticationException e) {
            log.info("Not authorized to access national registry");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return null;
        }
    }
}
