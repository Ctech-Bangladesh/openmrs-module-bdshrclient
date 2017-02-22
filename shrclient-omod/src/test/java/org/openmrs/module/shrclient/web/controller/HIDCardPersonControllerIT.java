package org.openmrs.module.shrclient.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.module.shrclient.model.HealthIdCard;
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
public class HIDCardPersonControllerIT extends BaseModuleWebContextSensitiveTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext wac;


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
    public void shouldGiveHealthIDCardForAPerson() throws Exception {
        executeDataSet("testDataSets/patientsWithHidsTestDs.xml");

        MvcResult result = mockMvc.perform(get("/hidcard/person/c98a1558-e131-56ty-8u6t-001e378eb67e"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        Map healthIdCard = new ObjectMapper().readValue(content, Map.class);

        HealthIdCard.HIDCardAddress hidCardAddress = new HealthIdCard().addAddress();
        hidCardAddress.setAddress1("lane 1");
        hidCardAddress.setAddress5("কালীগঞ্জ");
        hidCardAddress.setCountyDistrict("গাজীপুর");
        hidCardAddress.setStateProvince("ঢাকা");
        assertHealthIdCard(healthIdCard, "M", "Sayed", "Azam",
                null, null, "22-10-1992",
                "22-10-2016", "HID1", null,null, hidCardAddress);


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