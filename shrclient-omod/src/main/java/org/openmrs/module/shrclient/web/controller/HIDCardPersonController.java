package org.openmrs.module.shrclient.web.controller;

import org.apache.log4j.Logger;
import org.openmrs.module.shrclient.service.HIDCardPersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping(value = "/hidcard/person")
public class HIDCardPersonController {
    private static final Logger logger = Logger.getLogger(HIDCardPersonController.class);
    HIDCardPersonService hidCardPersonService;

    @Autowired
    public HIDCardPersonController(HIDCardPersonService hidCardPersonService) {
        this.hidCardPersonService = hidCardPersonService;
    }


    @RequestMapping(value = "/{personUUID}", method = RequestMethod.GET)
    @ResponseBody
    public Object getHIDCardforPerson(HttpServletRequest request, HttpServletResponse response, @PathVariable String personUUID) throws IOException {
        try {
            logger.info(String.format("Request received for HID card for person with UUID %s",personUUID));
            return hidCardPersonService.getHIDCardForPerson(personUUID);
        } catch (Exception e) {
            java.lang.String errorMessage = java.lang.String.format("Error finidng the patient with UUID :%s", personUUID);
            logger.error(errorMessage);
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
            return null;
        }
    }
}
