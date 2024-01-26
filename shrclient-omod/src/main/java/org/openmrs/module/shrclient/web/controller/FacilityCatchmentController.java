package org.openmrs.module.shrclient.web.controller;


import org.apache.log4j.Logger;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.shrclient.service.FacilityCatchmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping(value = "/facilityCatchments")
public class FacilityCatchmentController {
    private static final Logger log = Logger.getLogger(FacilityCatchmentController.class);

    @Autowired
    private FacilityCatchmentService facilityCatchmentService;

    @RequestMapping(method = RequestMethod.GET, value = "/findByFacility")
    @ResponseBody
    public Object findByFacility(@RequestParam int locationId, HttpServletResponse response) throws IOException {
        try {
            return facilityCatchmentService.getCatchmentsForFacility(locationId);
        } catch (APIAuthenticationException e) {
            log.info("Not authorized to access national registry");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return null;
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/findByCatchment")
    @ResponseBody
    public Object findByCatchment(@RequestParam String catchment, HttpServletResponse response) throws IOException {
        try {
            return facilityCatchmentService.getFacilitiesForCatchment(catchment);
        } catch (APIAuthenticationException e) {
            log.info("Not authorized to access national registry");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return null;
        }
    }


}
