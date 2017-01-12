package org.openmrs.module.shrclient.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.module.shrclient.model.HealthIdCard;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"})
public class HIDCardUserControllerIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @Before
    public void setup() throws Exception {
        executeDataSet("testDataSets/userAndRolesTestDs.xml");
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldGetAllTheUsers() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/getAll"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        User[] users = new ObjectMapper().readValue(content, User[].class);
        assertEquals(2, users.length);
    }

    @Test
    public void shouldGetAllThePatientsCreatedByAGivenUser() throws Exception {
        executeDataSet("testDataSets/patientsWithHidsTestDs.xml");
        MvcResult result = mockMvc.perform(get("/users/503/findAllPatients?from=2016-10-22&to=2016-12-22"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        HealthIdCard[] healthIdCards = new ObjectMapper().readValue(content, HealthIdCard[].class);
        assertEquals(3, healthIdCards.length);
        assertHealthIdCard(healthIdCards[0], "Male", "Sayed Azam", "22-10-1992",
                "22-10-2016", "HID1",
                "lane 1, Kaliganj, Gazipur, Dhaka");
        assertHealthIdCard(healthIdCards[1], "Transgender", "Farukh Engineer",
                "22-10-1993", "22-11-2016", "HID2",
                "lane 1, ward-01, Kaliganj Urban City, Unions of Kaliganj, Kaliganj, Gazipur, Dhaka");
        assertHealthIdCard(healthIdCards[2], "Female", "Babitha Chatterjee",
                "22-10-1992", "22-12-2016", "HID3",
                "lane 1, Kaliganj Urban City, Unions of Kaliganj, Kaliganj, Gazipur, Dhaka");

    }

    private void assertHealthIdCard(HealthIdCard healthIdCard, String expectedGender, String expectedName,
                                    String expectedDob, String expectedIssuedDate, String expectedHid,
                                    String expectedAddress) {
        assertEquals(expectedGender, healthIdCard.getGender());
        assertEquals(expectedName, healthIdCard.getName());
        assertEquals(expectedDob, healthIdCard.getDob());
        assertEquals(expectedIssuedDate, healthIdCard.getIssuedDate());
        assertEquals(expectedHid, healthIdCard.getHid());
        assertEquals(expectedAddress, healthIdCard.getAddress());
    }
}