package org.openmrs.module.shrclient.dao;

import org.junit.After;
import org.junit.Test;
import org.openmrs.module.shrclient.model.AddressHierarchyEntryTranslation;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.Assert.assertEquals;


@ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AddressHierarchyEntryTranslationRepositoryIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    AddressHierarchyEntryTranslationRepository addressHierarchyEntryTranslationRepository;

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }


    @Test
    public void shouldSaveAndGetTheAddressHierarchyEntryTranslation() throws Exception {
        int addressHierarchyEntryId = 1023456789;
        String localName = "dhaka_in_bangala";

        AddressHierarchyEntryTranslation entryTranslation = new AddressHierarchyEntryTranslation(addressHierarchyEntryId, localName);
        addressHierarchyEntryTranslationRepository.save(entryTranslation);
        assertEquals(localName, addressHierarchyEntryTranslationRepository.get(addressHierarchyEntryId).getLocalName());
    }
}