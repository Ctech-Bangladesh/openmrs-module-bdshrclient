package org.openmrs.module.shrclient.mapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.ProviderService;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.model.IdMappingType;
import org.openmrs.module.shrclient.model.ProviderEntry;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.SystemProperties;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.Assert.*;
import static org.openmrs.module.shrclient.mapper.ProviderMapper.RETIRE_REASON;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProviderMapperTest extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private ProviderService providerService;
    @Autowired
    private ProviderMapper providerMapper;
    @Autowired
    private PropertiesReader propertiesReader;
    @Autowired
    private IdMappingRepository idMappingRepository;

    private SystemProperties systemProperties;

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/providerDS.xml");
        systemProperties = new SystemProperties(
                propertiesReader.getFrProperties(),
                propertiesReader.getTrProperties(),
                propertiesReader.getPrProperties(),
                propertiesReader.getFacilityInstanceProperties(),
                propertiesReader.getMciProperties(),
                propertiesReader.getShrProperties());
    }

    @Test
    @Ignore
    public void shouldCreateNewProvider() throws Exception {
        String identifier = "1022";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true);
        assertNull(providerService.getProviderByIdentifier(identifier));

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProviderByIdentifier(identifier);
        assertEquals("Provider Name @ facility-name", provider.getName());
        assertFalse(provider.isRetired());
        assertEquals(1, provider.getAttributes().size());
        ProviderAttributeType providerAttributeType = providerService.getProviderAttributeType(1);
        ProviderAttribute organization = provider.getActiveAttributes(providerAttributeType).get(0);
        assertEquals("2222", organization.getValue());
        IdMapping idMapping = idMappingRepository.findByExternalId(identifier, IdMappingType.PROVIDER);
        assertNotNull(idMapping);
        assertEquals(provider.getUuid(), idMapping.getInternalId());
        assertTrue(idMapping.getUri().contains(identifier + ".json"));
        assertNotNull(idMapping.getCreatedAt());
    }

    @Test
    @Ignore
    public void shouldUpdateProviderNameWithFacilityNameIfMapped() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true);
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertEquals("test provider", existingProvider.getName());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertEquals("Provider Name @ facility-name", provider.getName());
    }

    @Test
    @Ignore
    public void shouldUpdateProviderName() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", false);
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertEquals("test provider", existingProvider.getName());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertEquals("Provider Name", provider.getName());
    }

    @Test
    @Ignore
    public void shouldUpdateProviderOrganization() throws Exception {
        ProviderAttributeType organizationAttributeType = providerService.getProviderAttributeType(1);
        ProviderEntry providerEntry = getProviderEntry("1024", "1", true);
        Provider existingProvider = providerService.getProvider(23);
        ProviderAttribute providerAttribute = existingProvider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("1022", providerAttribute.getValue());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(23);
        ProviderAttribute organization = provider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("2222", organization.getValue());
    }

    @Test
    @Ignore
    public void shouldRetireProviderIfNotActive() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "0", true);
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertFalse(existingProvider.isRetired());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertTrue(provider.isRetired());
        assertEquals(RETIRE_REASON, provider.getRetireReason());
    }

    @Test
    @Ignore
    public void shouldUnRetireProviderIfRetired() throws Exception {
        String identifier = "1025";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true);
        Provider existingProvider = providerService.getProvider(24);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertTrue(existingProvider.isRetired());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(24);
        assertEquals(identifier, provider.getIdentifier());
        assertFalse(provider.isRetired());
    }

    private ProviderEntry getProviderEntry(String identifier, String active, boolean isOrganizationMapped) {
        ProviderEntry providerEntry = new ProviderEntry();
        providerEntry.setId(identifier);
        providerEntry.setName("Provider Name");
        providerEntry.setActive(active);
        if (isOrganizationMapped) {
            ProviderEntry.Organization organization = providerEntry.new Organization();
            organization.setReference("http://something/2222.json");
            organization.setDisplay("facility-name");
            providerEntry.setOrganization(organization);
        }
        return providerEntry;
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }
}