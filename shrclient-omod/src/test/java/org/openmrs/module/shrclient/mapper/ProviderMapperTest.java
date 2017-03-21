package org.openmrs.module.shrclient.mapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.utils.DateUtil;
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
import static org.openmrs.module.fhir.utils.DateUtil.SIMPLE_DATE_FORMAT;
import static org.openmrs.module.fhir.utils.DateUtil.toDateString;
import static org.openmrs.module.shrclient.mapper.ProviderMapper.PERSON_RETIRE_REASON;
import static org.openmrs.module.shrclient.mapper.ProviderMapper.PROVIDER_RETIRE_REASON;

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
    public void shouldCreateNewProviderWithPerson() throws Exception {
        String identifier = "1022";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true, "Male");
        assertNull(providerService.getProviderByIdentifier(identifier));

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProviderByIdentifier(identifier);
        assertEquals("Provider Name @ facility-name", provider.getName());
        assertFalse(provider.getRetired());
        assertEquals(1, provider.getAttributes().size());
        ProviderAttributeType providerAttributeType = providerService.getProviderAttributeType(1);
        ProviderAttribute organization = provider.getActiveAttributes(providerAttributeType).get(0);
        assertEquals("2222", organization.getValue());
        IdMapping idMapping = idMappingRepository.findByExternalId(identifier, IdMappingType.PROVIDER);
        assertNotNull(idMapping);
        assertEquals(provider.getUuid(), idMapping.getInternalId());
        assertTrue(idMapping.getUri().contains(identifier + ".json"));
        assertNotNull(idMapping.getCreatedAt());

        Person person = provider.getPerson();
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("M", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    @Test
    public void shouldMapProviderToAPersonIfNotAlreadyMappedWhenProviderIsUpdated() throws Exception {
        ProviderAttributeType organizationAttributeType = providerService.getProviderAttributeType(1);
        ProviderEntry providerEntry = getProviderEntry("1024", "1", true, "Trans gender");
        Provider existingProvider = providerService.getProvider(23);
        ProviderAttribute providerAttribute = existingProvider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("1022", providerAttribute.getValue());
        assertNull(existingProvider.getPerson());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(23);
        ProviderAttribute organization = provider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("2222", organization.getValue());
        Person person = provider.getPerson();
        assertNotNull(person);
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("O", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    @Test
    public void shouldUpdatePersonAndProviderNameWithFacilityNameIfMapped() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true, "Female");
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertEquals("test provider", existingProvider.getName());
        Person person = existingProvider.getPerson();
        assertEquals(existingProvider.getName(), person.getPersonName().getFullName());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertEquals("Provider Name @ facility-name", provider.getName());
        person = provider.getPerson();
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("F", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    @Test
    public void shouldUpdateProviderName() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", false, "Others");
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertEquals("test provider", existingProvider.getName());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertEquals("Provider Name @", provider.getName());
        Person person = provider.getPerson();
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("O", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    @Test
    public void shouldUpdateProviderOrganization() throws Exception {
        ProviderAttributeType organizationAttributeType = providerService.getProviderAttributeType(1);
        ProviderEntry providerEntry = getProviderEntry("1024", "1", true, "Trans gender");
        Provider existingProvider = providerService.getProvider(23);
        ProviderAttribute providerAttribute = existingProvider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("1022", providerAttribute.getValue());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(23);
        ProviderAttribute organization = provider.getActiveAttributes(organizationAttributeType).get(0);
        assertEquals("2222", organization.getValue());
        Person person = provider.getPerson();
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("O", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    @Test
    public void shouldRetireProviderAndMappedPersonIfNotActive() throws Exception {
        String identifier = "1023";
        ProviderEntry providerEntry = getProviderEntry(identifier, "0", true, "Male");
        Provider existingProvider = providerService.getProvider(22);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertFalse(existingProvider.getRetired());
        assertFalse(existingProvider.getPerson().getVoided());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(22);
        assertEquals(identifier, provider.getIdentifier());
        assertTrue(provider.getRetired());
        assertEquals(PROVIDER_RETIRE_REASON, provider.getRetireReason());

        Person person = provider.getPerson();
        assertTrue(provider.getPerson().getVoided());
        assertEquals(PERSON_RETIRE_REASON, provider.getPerson().getVoidReason());
        assertEquals("M", provider.getPerson().getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
    }

    @Test
    public void shouldUnRetireProviderAndMappedPerson() throws Exception {
        String identifier = "1025";
        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true, "Male");
        Provider existingProvider = providerService.getProvider(24);
        assertEquals(identifier, existingProvider.getIdentifier());
        assertTrue(existingProvider.getRetired());
        assertTrue(existingProvider.getPerson().getVoided());

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProvider(24);
        assertEquals(identifier, provider.getIdentifier());
        assertFalse(provider.getRetired());
        Person person = provider.getPerson();
        Context.flushSession();
        Context.clearSession();
        assertFalse(Context.getPersonService().getPerson(123).getVoided());
        assertEquals("M", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
    }

    @Test
    public void shouldTrimFamilyNameForPersonWhenRequired() throws Exception {
        String orgName = "International Centre for Diarrhoeal Disease Research, Bangladesh (icddr,b)";
        String identifier = "1022";

        ProviderEntry providerEntry = getProviderEntry(identifier, "1", true, "Male");
        ProviderEntry.Organization organization = providerEntry.new Organization();
        organization.setReference("http://something/2222.json");
        organization.setDisplay(orgName);
        providerEntry.setOrganization(organization);
        assertNull(providerService.getProviderByIdentifier(identifier));

        providerMapper.createOrUpdate(providerEntry, systemProperties);

        Provider provider = providerService.getProviderByIdentifier(identifier);
        assertEquals("Provider Name @ International Centre for Diarrhoeal Disease Rese", provider.getName());
        assertFalse(provider.getRetired());
        Person person = provider.getPerson();
        assertEquals(provider.getName(), person.getPersonName().getFullName());
        assertEquals("M", person.getGender());
        assertEquals("1995-12-13", toDateString(person.getBirthdate(), SIMPLE_DATE_FORMAT));
        assertFalse(person.getPersonVoided());
    }

    private ProviderEntry getProviderEntry(String identifier, String active, boolean isOrganizationMapped, String gender) {
        ProviderEntry providerEntry = new ProviderEntry();
        providerEntry.setId(identifier);
        providerEntry.setName("Provider Name");
        providerEntry.setActive(active);
        providerEntry.setGender(gender);
        providerEntry.setBirthDate("1995-12-13");
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
