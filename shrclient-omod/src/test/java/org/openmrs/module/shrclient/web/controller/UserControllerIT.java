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
public class UserControllerIT extends BaseModuleWebContextSensitiveTest {
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
        MvcResult result = mockMvc.perform(get("/users/503/findAllPatients?from=22-10-2016&to=22-12-2016"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        HealthIdCard[] healthIdCards = new ObjectMapper().readValue(content, HealthIdCard[].class);
        assertEquals(3, healthIdCards.length);
    }
}