package org.openmrs.module.shrclient.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.module.shrclient.model.User;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
        Map[] healthIdCards = new ObjectMapper().readValue(content, Map[].class);
        assertEquals(4, healthIdCards.length);
        assertHealthIdCard(healthIdCards[0], "M", "Sayed Azam", "", "22-10-1992",
                "22-10-2016", "HID1",
                "lane 1, কালীগঞ্জ, গাজীপুর, ঢাকা");
        assertHealthIdCard(healthIdCards[1], "O", "Farukh Engineer",
                "", "22-10-1993", "22-11-2016", "HID2",
                "lane 1, ওয়ার্ডে কোন -1, বাহাদুরসাদী, কালীগঞ্জ, গাজীপুর, ঢাকা");
        assertHealthIdCard(healthIdCards[2], "F", "Babitha Chatterjee",
                "", "22-10-1992", "22-12-2016", "HID3",
                "lane 1, Urban Ward No-01, Kaliganj Paurashava, কালীগঞ্জ, গাজীপুর, ঢাকা");
        assertHealthIdCard(healthIdCards[3], "F", "Babitha Chatterjee",
                "বাবিথা", "22-10-1992", "22-12-2016", "HID4",
                "lane 1, Urban Ward No-01, Kaliganj Paurashava, কালীগঞ্জ, গাজীপুর, ঢাকা");
    }

    @Test
    public void shouldNotGetHealthIdCardsForVoidedOrIssuedHIDs() throws Exception {
        executeDataSet("testDataSets/patientsWithVoidedHidIdentifiersOrIssuedHIDCard.xml");
        MvcResult result = mockMvc.perform(get("/users/503/findAllPatients?from=2016-10-22&to=2016-12-22"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        Map[] healthIdCards = new ObjectMapper().readValue(content, Map[].class);
        assertEquals(2, healthIdCards.length);
        assertHealthIdCard(healthIdCards[0], "M", "Sayed Azam",
                "", "22-10-1992",
                "22-10-2016", "HID1",
                "lane 1, কালীগঞ্জ, গাজীপুর, ঢাকা");
        assertHealthIdCard(healthIdCards[1], "O", "Farukh Engineer",
                "", "22-10-1993", "22-11-2016", "HID2",
                "lane 1, ওয়ার্ডে কোন -1, বাহাদুরসাদী, কালীগঞ্জ, গাজীপুর, ঢাকা");
    }

    private void assertHealthIdCard(Map healthIdCard, String expectedGender, String expectedEnglishName,
                                    String expectedBengaliName, String expectedDob, String expectedIssuedDate, String expectedHid,
                                    String expectedAddress) {
        assertEquals(expectedGender, healthIdCard.get("gender"));
        assertEquals(expectedEnglishName, healthIdCard.get("englishName"));
        assertEquals(expectedBengaliName, healthIdCard.get("banglaName"));
        assertEquals(expectedDob, healthIdCard.get("dob"));
        assertEquals(expectedIssuedDate, healthIdCard.get("issuedDate"));
        assertEquals(expectedHid, healthIdCard.get("hid"));
        assertEquals(expectedAddress, healthIdCard.get("address"));
    }
}