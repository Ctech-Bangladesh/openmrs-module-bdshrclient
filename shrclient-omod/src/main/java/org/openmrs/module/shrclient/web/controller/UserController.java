package org.openmrs.module.shrclient.web.controller;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openmrs.api.PatientService;
import org.openmrs.api.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping(value = "/users")
public class UserController {
    @Autowired
    private PatientService patientService;
    @Autowired
    private UserService userService;

    @RequestMapping(value = "/getAll", method = RequestMethod.GET)
    @ResponseBody
    public List<User> findAllUsers() {
        List<org.openmrs.User> allUsers = userService.getAllUsers();
        ArrayList<User> users = new ArrayList<>();
        for (org.openmrs.User user : allUsers) {
            users.add(new User(user.getId(), user.getDisplayString()));
        }
        return users;
    }

    @RequestMapping(value = "/{userId}/findAllPatients", method = RequestMethod.GET)
    public List<HealthIdCard> findAllByUserBetweenDates(@PathVariable String userId
            , @RequestParam(value = "from") String from, @RequestParam(value = "to") String to) {
        List<HealthIdCard> cards = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            HealthIdCard healthIdCard = new HealthIdCard();
            healthIdCard.setName(String.valueOf(Math.random() * 100000));
            healthIdCard.setAddress(String.valueOf(Math.random() * 100000) + String.valueOf(Math.random() * 100000));
            healthIdCard.setDob(new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
            healthIdCard.setGender("male");
            healthIdCard.setHid(String.valueOf(new Date().getTime()));
            cards.add(healthIdCard);
        }
        return cards;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    private class User {
        @JsonProperty("id")
        int id;
        @JsonProperty("name")
        String name;

        public User() {
        }

        public User(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
    private class HealthIdCard {
        private String name;
        private String gender;
        private String dob;
        private String address;
        private String hid;

        public HealthIdCard() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getDob() {
            return dob;
        }

        public void setDob(String dob) {
            this.dob = dob;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getHid() {
            return hid;
        }

        public void setHid(String hid) {
            this.hid = hid;
        }
    }
}
