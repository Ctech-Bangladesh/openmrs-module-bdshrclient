package org.openmrs.module.shrclient.web.controller;

import org.openmrs.Role;
import org.openmrs.api.UserService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.module.shrclient.service.HealthIdCardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.fhir.OpenMRSConstants.HID_PRINT_PAGE_USER_ROLE;

@Controller
@RequestMapping(value = "/users")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private GlobalPropertyLookUpService globalPropertyLookUpService;
    @Autowired
    private HealthIdCardService healthIdCardService;

    @RequestMapping(value = "/getAll", method = RequestMethod.GET)
    @ResponseBody
    public List<User> findAllUsers() {
        Role role = userService.getRole(HID_PRINT_PAGE_USER_ROLE);
        if (null == role) return Collections.emptyList();
        List<org.openmrs.User> allUsers = userService.getAllUsers();
        ArrayList<User> users = new ArrayList<>();
        for (org.openmrs.User user : allUsers) {
            if (user.getAllRoles().contains(role)) {
                users.add(new User(user.getId(), user.getDisplayString()));
            }
        }
        return users;
    }

    @RequestMapping(value = "/{userId}/findAllPatients", method = RequestMethod.GET)
    @ResponseBody
    public List<HealthIdCard> findAllByUserBetweenDates(@PathVariable int userId
            , @RequestParam(value = "from") String from, @RequestParam(value = "to") String to) {
        return healthIdCardService.getAllCardsByQuery(userId, from, to);
    }
}
