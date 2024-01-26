package org.openmrs.module.shrclient.handlers;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.AddressHierarchyLevel;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.shrclient.dao.AddressHierarchyEntryTranslationRepository;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.mapper.AddressHierarchyEntryMapper;
import org.openmrs.module.shrclient.model.AddressHierarchyEntryTranslation;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.ScheduledTaskHistory;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LocationPullIT extends BaseModuleWebContextSensitiveTest {
    private LocationPull locationPull;
    @Mock
    private ScheduledTaskHistory scheduledTaskHistory;

    @Autowired
    private ApplicationContext springContext;
    @Autowired
    private PropertiesReader propertiesReader;
    @Autowired
    private AddressHierarchyEntryTranslationRepository entryTranslationRepository;

    private RestClient restClient;
    private AddressHierarchyService addressHierarchyService;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9997);

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/address_hierarchy_levels.xml");
        MockitoAnnotations.initMocks(this);
        restClient = new ClientRegistry(propertiesReader, new IdentityStore()).getLRClient();
        addressHierarchyService = Context.getService(AddressHierarchyService.class);
        locationPull = new LocationPull(propertiesReader, restClient, addressHierarchyService,
                scheduledTaskHistory, new AddressHierarchyEntryMapper(), entryTranslationRepository);

        stubFor(get(urlPathMatching("/api/1.0/locations/list/district")).willReturn(aResponse().withBody("[]")));
        stubFor(get(urlPathMatching("/api/1.0/locations/list/upazila")).willReturn(aResponse().withBody("[]")));
        stubFor(get(urlPathMatching("/api/1.0/locations/list/union")).willReturn(aResponse().withBody("[]")));
        stubFor(get(urlPathMatching("/api/1.0/locations/list/ward")).willReturn(aResponse().withBody("[]")));
    }

    @Test
    public void shouldPullLocationsAndSave() throws Exception {
        stubFor(get(urlPathMatching("/api/1.0/locations/list/division"))
                .withHeader("X-Auth-Token", equalTo("xyz"))
                .withHeader("client_id", equalTo("12345"))
                .willReturn(aResponse().withBody(asString("LRResponse/DivisionLevelResponseFromLocationRegistry.json")))
        );
        stubFor(get(urlPathMatching("/api/1.0/locations/list/paurasava"))
                .withHeader("X-Auth-Token", equalTo("xyz"))
                .withHeader("client_id", equalTo("12345"))
                .willReturn(aResponse().withBody(asString("LRResponse/PaurasavaLevelResponseFromLocationRegistry.json")))
        );

        locationPull.synchronize();
        AddressHierarchyLevel levelDivision = addressHierarchyService.getAddressHierarchyLevel(1);
        AddressHierarchyLevel levelPaurasava = addressHierarchyService.getAddressHierarchyLevel(4);
        assertThat(addressHierarchyService.getAddressHierarchyEntryCount(), is(17));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelDivision), is(7));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelPaurasava), is(10));

        AddressHierarchyEntry dhakaDivision = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levelDivision, "Dhaka").get(0);
        AddressHierarchyEntryTranslation dhakaEntryTranslation = entryTranslationRepository.get(dhakaDivision.getId());
        assertNotNull(dhakaEntryTranslation);
        assertEquals("ঢাকা", dhakaEntryTranslation.getLocalName());

        AddressHierarchyEntry amtaliPaurasava = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levelPaurasava, "AMTALI PAURASAVA").get(0);
        AddressHierarchyEntryTranslation amtaliEntryTranslation = entryTranslationRepository.get(amtaliPaurasava.getId());
        assertNotNull(amtaliEntryTranslation);
        assertEquals("আমতলী", amtaliEntryTranslation.getLocalName());
    }

    @Test
    public void shouldPullLocationsAndUpdateExisting() throws Exception {
        executeDataSet("testDataSets/address_hierarchy_entries.xml");
        stubFor(get(urlPathMatching("/api/1.0/locations/list/division"))
                .withHeader("X-Auth-Token", equalTo("xyz"))
                .withHeader("client_id", equalTo("12345"))
                .willReturn(aResponse().withBody(asString("LRResponse/DivisionLevelResponseFromLocationRegistry.json")))
        );
        stubFor(get(urlPathMatching("/api/1.0/locations/list/paurasava"))
                .withHeader("X-Auth-Token", equalTo("xyz"))
                .withHeader("client_id", equalTo("12345"))
                .willReturn(aResponse().withBody(asString("LRResponse/PaurasavaLevelResponseFromLocationRegistry.json")))
        );

        AddressHierarchyLevel levelDivision = addressHierarchyService.getAddressHierarchyLevel(1);
        AddressHierarchyLevel levelPaurasava = addressHierarchyService.getAddressHierarchyLevel(4);
        assertThat(addressHierarchyService.getAddressHierarchyEntryCount(), is(6));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelDivision), is(1));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelPaurasava), is(1));

        locationPull.synchronize();

        assertThat(addressHierarchyService.getAddressHierarchyEntryCount(), is(22));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelDivision), is(7));
        assertThat(addressHierarchyService.getAddressHierarchyEntryCountByLevel(levelPaurasava), is(11));

        AddressHierarchyEntry dhakaDivision = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levelDivision, "Dhaka").get(0);
        AddressHierarchyEntryTranslation dhakaEntryTranslation = entryTranslationRepository.get(dhakaDivision.getId());
        assertNotNull(dhakaEntryTranslation);
        assertEquals("ঢাকা", dhakaEntryTranslation.getLocalName());

        AddressHierarchyEntry amtaliPaurasava = addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(levelPaurasava, "AMTALI PAURASAVA").get(0);
        AddressHierarchyEntryTranslation amtaliEntryTranslation = entryTranslationRepository.get(amtaliPaurasava.getId());
        assertNotNull(amtaliEntryTranslation);
        assertEquals("আমতলী", amtaliEntryTranslation.getLocalName());
    }

    private String asString(String pathname) throws IOException {
        Resource resource = springContext.getResource(pathname);
        return Resources.toString(resource.getURL(), Charset.forName("UTF-8"));
    }
}