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

        HealthIdCard.HIDCardAddress hidCardAddress2 = new HealthIdCard().addAddress();
        hidCardAddress2.setAddress1("lane 1");
        hidCardAddress2.setAddress3("Urban Ward No-01");
        hidCardAddress2.setAddress4("Kaliganj Paurashava");
        hidCardAddress2.setAddress5("কালীগঞ্জ");
        hidCardAddress2.setCountyDistrict("গাজীপুর");
        hidCardAddress2.setStateProvince("ঢাকা");

        assertHealthIdCard(healthIdCards[0], "F", "Babitha", "Banarjee", "বাবিথা", null,
                "22-10-1992", "22-12-2016", "HID4", "NID2","BRN1", hidCardAddress2);
        assertHealthIdCard(healthIdCards[1], "F", "Babitha", "Chatterjee", null, null,
                "22-10-1992", "22-12-2016", "HID3", null,null, hidCardAddress2);

        HealthIdCard.HIDCardAddress hidCardAddress1 = new HealthIdCard().addAddress();
        hidCardAddress1.setAddress1("lane 1");
        hidCardAddress1.setAddress2("ওয়ার্ডে কোন -1");
        hidCardAddress1.setAddress3("বাহাদুরসাদী");
        hidCardAddress1.setAddress5("কালীগঞ্জ");
        hidCardAddress1.setCountyDistrict("গাজীপুর");
        hidCardAddress1.setStateProvince("ঢাকা");
        assertHealthIdCard(healthIdCards[2], "O", "Farukh", "Engineer",
                null, null, "22-10-1993", "22-11-2016",
                "HID2", null,null,hidCardAddress1);

        HealthIdCard.HIDCardAddress hidCardAddress = new HealthIdCard().addAddress();
        hidCardAddress.setAddress1("lane 1");
        hidCardAddress.setAddress5("কালীগঞ্জ");
        hidCardAddress.setCountyDistrict("গাজীপুর");
        hidCardAddress.setStateProvince("ঢাকা");
        assertHealthIdCard(healthIdCards[3], "M", "Sayed", "Azam",
                null, null, "22-10-1992",
                "22-10-2016", "HID1", null,null, hidCardAddress);
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

        HealthIdCard.HIDCardAddress hidCardAddress1 = new HealthIdCard().addAddress();
        hidCardAddress1.setAddress1("lane 1");
        hidCardAddress1.setAddress2("ওয়ার্ডে কোন -1");
        hidCardAddress1.setAddress3("বাহাদুরসাদী");
        hidCardAddress1.setAddress5("কালীগঞ্জ");
        hidCardAddress1.setCountyDistrict("গাজীপুর");
        hidCardAddress1.setStateProvince("ঢাকা");
        assertHealthIdCard(healthIdCards[0], "O", "Farukh", "Engineer", null,
                null, "22-10-1993", "22-11-2016", "HID2",null,null, hidCardAddress1);

        HealthIdCard.HIDCardAddress hidCardAddress = new HealthIdCard().addAddress();
        hidCardAddress.setAddress1("lane 1");
        hidCardAddress.setAddress5("কালীগঞ্জ");
        hidCardAddress.setCountyDistrict("গাজীপুর");
        hidCardAddress.setStateProvince("ঢাকা");
        assertHealthIdCard(healthIdCards[1], "M", "Sayed", "Azam",
                null, null, "22-10-1992",
                "22-10-2016", "HID1", "NID1","BRN2", hidCardAddress);

    }

    private void assertHealthIdCard(Map healthIdCard, String expectedGender, String expectedGivenName, String expectedFamilyName,
                                    String expectedGivenNameLocal, String expectedFamilyNameLocal, String expectedDob, String expectedIssuedDate, String expectedHid,
                                    String expectednid,String expectedbrn, HealthIdCard.HIDCardAddress expectedAddress) {
        assertEquals(expectedGender, healthIdCard.get("gender"));
        assertEquals(expectedGivenName, healthIdCard.get("givenName"));
        assertEquals(expectedFamilyName, healthIdCard.get("familyName"));
        assertEquals(expectedGivenNameLocal, healthIdCard.get("givenNameLocal"));
        assertEquals(expectedFamilyNameLocal, healthIdCard.get("familyNameLocal"));
        assertEquals(expectedDob, healthIdCard.get("dob"));
        assertEquals(expectedIssuedDate, healthIdCard.get("issuedDate"));
        assertEquals(expectedHid, healthIdCard.get("hid"));
        assertEquals(expectednid, healthIdCard.get("nid"));
        assertEquals(expectedbrn, healthIdCard.get("brn"));
        Map address = (Map) healthIdCard.get("address");
        assertEquals(expectedAddress.getAddress1(), address.get("address1"));
        assertEquals(expectedAddress.getAddress2(), address.get("address2"));
        assertEquals(expectedAddress.getAddress3(), address.get("address3"));
        assertEquals(expectedAddress.getAddress4(), address.get("address4"));
        assertEquals(expectedAddress.getAddress5(), address.get("address5"));
        assertEquals(expectedAddress.getCountyDistrict(), address.get("countyDistrict"));
        assertEquals(expectedAddress.getStateProvince(), address.get("stateProvince"));
    }
}