package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Composition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.FhirContextHelper;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openmrs.module.fhir.FHIRProperties.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CompositionBundleCreatorIT extends BaseModuleWebContextSensitiveTest {

    @Autowired
    private CompositionBundleCreator compositionBundleCreator;

    private static String HEALTH_ID = "1234512345123";

    @Before
    public void setUp() throws Exception {
        executeDataSet("testDataSets/shrClientBundleCreatorTestDS.xml");
    }

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldWireAllResourceHandlers() throws Exception {
        ensureBundleCreatorHasResourceHandlers("obsResourceHandlers");
        ensureBundleCreatorHasResourceHandlers("orderResourceHandlers");
    }

    @Test
    public void shouldCreateFhirBundle() throws Exception {
        String facilityId = "10000036";
        Bundle bundle = compositionBundleCreator.create(Context.getEncounterService().getEncounter(36), HEALTH_ID, getSystemProperties(facilityId));
        assertNotNull(bundle);
        String bundleXml = FhirContextHelper.getFhirContext().newXmlParser().encodeResourceToString(bundle);
        assertNotNull(bundleXml);
    }

    @Test
    public void shouldPopulateCompositionType() throws Exception {
        Bundle bundle = compositionBundleCreator.create(Context.getEncounterService().getEncounter(36), HEALTH_ID, getSystemProperties("12345"));
        assertNotNull(bundle);
        Composition composition = FHIRBundleHelper.getComposition(bundle);
        Coding type = composition.getType().getCoding().get(0);
        assertEquals(LOINC_CODE_DETAILS_NOTE, type.getCode());
        assertEquals(FHIR_DOC_TYPECODES_URL, type.getSystem());
        assertEquals(LOINC_DETAILS_NOTE_DISPLAY, type.getDisplay());
    }

    private void ensureBundleCreatorHasResourceHandlers(String handlerName) throws NoSuchFieldException, IllegalAccessException {
        final Field field = compositionBundleCreator.getClass().getDeclaredField(handlerName);
        field.setAccessible(true);
        Object instances = field.get(compositionBundleCreator);
        assertNotNull(instances);
        if (instances instanceof List) {
            assertTrue(((List) instances).size() > 0);
        }
    }
}
