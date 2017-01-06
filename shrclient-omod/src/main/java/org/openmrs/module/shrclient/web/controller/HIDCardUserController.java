package org.openmrs.module.shrclient.web.controller;

import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.module.shrclient.service.HIDCardUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping(value = "/users")
public class HIDCardUserController {
    private HIDCardUserService hidCardUserService;

    @Autowired
    public HIDCardUserController(HIDCardUserService hidCardUserService) {
        this.hidCardUserService = hidCardUserService;
    }

    @RequestMapping(value = "/getAll", method = RequestMethod.GET)
    @ResponseBody
    public List<User> findAllUsers() {
        return hidCardUserService.getAllUsers();
    }

    @RequestMapping(value = "/{userId}/findAllPatients", method = RequestMethod.GET)
    @ResponseBody
    public List<HealthIdCard> findAllPatientsByUserBetweenDates(@PathVariable int userId
            , @RequestParam(value = "from") String from, @RequestParam(value = "to") String to) {
        from = from + " 00:00:00";
        to = to + " 23:59:59";
        return hidCardUserService.getAllCardsByUserWithinDateRange(userId, from, to);
    }
}
