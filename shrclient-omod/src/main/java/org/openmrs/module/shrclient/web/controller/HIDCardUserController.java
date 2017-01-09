package org.openmrs.module.shrclient.web.controller;

import org.apache.log4j.Logger;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.module.shrclient.service.HIDCardUserService;
import org.openmrs.module.shrclient.service.impl.HIDCardUserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping(value = "/users")
public class HIDCardUserController {
    private static final Logger logger = Logger.getLogger(HIDCardUserServiceImpl.class);
    private HIDCardUserService hidCardUserService;

    @Autowired
    public HIDCardUserController(HIDCardUserService hidCardUserService) {
        this.hidCardUserService = hidCardUserService;
    }

    @RequestMapping(value = "/getAll", method = RequestMethod.GET)
    @ResponseBody
    public List<User> findAllUsers(MciPatientSearchRequest request, HttpServletResponse response) throws IOException {
        try {
            return hidCardUserService.getAllUsersWithFieldRole();
        } catch (APIAuthenticationException e) {
            logger.info("Not Authorized to access Print Health Id Module");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @RequestMapping(value = "/{userId}/findAllPatients", method = RequestMethod.GET)
    @ResponseBody
    public List<HealthIdCard> findAllPatientsByUserBetweenDates(MciPatientSearchRequest request, HttpServletResponse response,
                                                                @PathVariable int userId
            , @RequestParam(value = "from") String from, @RequestParam(value = "to") String to) throws IOException {
        from = from + " 00:00:00";
        to = to + " 23:59:59";
        try {
            return hidCardUserService.getAllCardsByUserWithinDateRange(userId, from, to);
        } catch (APIAuthenticationException e) {
            logger.info("Not Authorized to access Print Health Id Module");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
