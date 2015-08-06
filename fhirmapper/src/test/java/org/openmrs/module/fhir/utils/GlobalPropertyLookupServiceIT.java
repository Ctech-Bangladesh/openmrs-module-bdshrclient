package org.openmrs.module.fhir.utils;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.assertEquals;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GlobalPropertyLookupServiceIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    GlobalPropertyLookUpService globalPropertyLookUpService;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/omrsGlobalPropertyTestDS.xml");
    }

    @Test
    public void shouldReturnConfiguredGlobalPropertyValue() throws Exception {
        assertEquals(555, globalPropertyLookUpService.getGlobalPropertyValue("concept.causeOfDeath").intValue());
    }
}