package org.openmrs.module.fhir.mapper.bundler;

import org.hl7.fhir.dstu3.model.*;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.FHIRProperties;
import org.openmrs.module.fhir.MapperTestHelper;
import org.openmrs.module.fhir.TestFhirFeedHelper;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {"classpath:TestingApplicationContext.xml"}, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GenericOrderFulfillmentMapperIT extends BaseModuleWebContextSensitiveTest {
    @Autowired
    private GenericOrderFulfillmentMapper genericOrderFulfillmentMapper;
    @Autowired
    private ObsService obsService;

    private final String patientRef = "http://mci.com/patients/HID-123";
    private final String fhirEncounterId = "SHR-ENC1";

    @After
    public void tearDown() throws Exception {
        deleteAllData();
    }

    @Test
    public void shouldHandleRadiologyFulfillment() throws Exception {
        executeDataSet("testDataSets/radiologyFulfillmentDS.xml");
        Obs observation = obsService.getObs(501);
        assertTrue(genericOrderFulfillmentMapper.canHandle(observation));
    }

    @Test
    public void shouldNotHandleProcedureFulfillment() throws Exception {
        executeDataSet("testDataSets/procedureFulfillmentDS.xml");
        Obs fulfilmentObs = obsService.getObs(1011);
        assertFalse(genericOrderFulfillmentMapper.canHandle(fulfilmentObs));
    }

    @Test
    public void shouldNotHandleRadiologyFulfillment() throws Exception {
        executeDataSet("testDataSets/labResultDS.xml");
        Obs observation = obsService.getObs(1);
        assertFalse(genericOrderFulfillmentMapper.canHandle(observation));
    }

    @Test
    public void shouldMapRadiologyFulfillment() throws Exception {
        executeDataSet("testDataSets/radiologyFulfillmentDS.xml");
        Obs observation = obsService.getObs(501);
        List<FHIRResource> resources = genericOrderFulfillmentMapper.map(observation, createFhirEncounter(), getSystemProperties("1"));
        assertNotNull(resources);
        assertEquals(3, resources.size());

        FHIRResource resource = TestFhirFeedHelper.getFirstResourceByType(new DiagnosticReport().getResourceType().name(), resources);
        DiagnosticReport report = (DiagnosticReport) resource.getResource();
        assertEquals(1, report.getBasedOn().size());
        String requestUrl = "http://localhost:9997/patients/hid/encounters/shr-enc-1#ProcedureRequest/6d0ae396-efab-4629-1930-f15206e63ab0";
        assertEquals(requestUrl, report.getBasedOn().get(0).getReference());
        Coding categoryCoding = report.getCategory().getCodingFirstRep();
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_DISPLAY, categoryCoding.getDisplay());
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE, categoryCoding.getCode());
        assertEquals(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL, categoryCoding.getSystem());
        assertEquals(1, report.getCode().getCoding().size());
        assertTrue(MapperTestHelper.containsCoding(report.getCode().getCoding(), "501qb827-a67c-4q1f-a705-e5efe0q6a972",
                "http://localhost:9997/openmrs/ws/rest/v1/tr/concept/501qb827-a67c-4q1f-a705-e5efe0q6a972", "X-Ray Right Chest"));

        assertEquals(2, TestFhirFeedHelper.getResourceByType(new Observation().getResourceType().name(), resources).size());

        Observation typeOfRadiologyObs = getObsAsResult(report, resources, "Type of Radiology Order");
        assertNotNull(typeOfRadiologyObs);
        Coding codingDt = ((CodeableConcept) typeOfRadiologyObs.getValue()).getCodingFirstRep();
        assertEquals("501qb827-a67c-4q1f-a705-e5efe0q6a972", codingDt.getCode());
        assertEquals("http://localhost:9997/openmrs/ws/rest/v1/tr/concept/501qb827-a67c-4q1f-a705-e5efe0q6a972", codingDt.getSystem());
        assertEquals("X-Ray Right Chest", codingDt.getDisplay());

        Observation dateOfRadiologyObs = getObsAsResult(report, resources, "Date of Radiology Order");
        assertNotNull(dateOfRadiologyObs);
        assertEquals(DateUtil.parseDate("2008-08-18"), ((DateTimeType) dateOfRadiologyObs.getValue()).getValue());
    }

    @Test
    public void shouldMapLocalRadiologyFulfillment() throws Exception {
        executeDataSet("testDataSets/radiologyFulfillmentDS.xml");
        Obs observation = obsService.getObs(701);
        List<FHIRResource> resources = genericOrderFulfillmentMapper.map(observation, createFhirEncounter(), getSystemProperties("1"));
        assertNotNull(resources);
        assertEquals(4, resources.size());

        FHIRResource resource = TestFhirFeedHelper.getFirstResourceByType(new DiagnosticReport().getResourceType().name(), resources);
        DiagnosticReport report = (DiagnosticReport) resource.getResource();
        String requestUrl = "http://localhost:9997/patients/hid/encounters/shr-enc-1#ProcedureRequest/6d0ae396-efab-4629-1930-f16206e63ab0";
        assertEquals(requestUrl, report.getBasedOn().get(0).getReference());
        Coding categoryCoding = report.getCategory().getCodingFirstRep();
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_DISPLAY, categoryCoding.getDisplay());
        assertEquals(FHIRProperties.FHIR_DIAGNOSTIC_REPORT_CATEGORY_RADIOLOGY_CODE, categoryCoding.getCode());
        assertEquals(FHIRProperties.FHIR_V2_VALUESET_DIAGNOSTIC_REPORT_CATEGORY_URL, categoryCoding.getSystem());
        assertEquals(1, report.getCode().getCoding().size());
        assertTrue(MapperTestHelper.containsCoding(report.getCode().getCoding(), null, null, "X-ray left hand"));

        assertEquals(3, TestFhirFeedHelper.getResourceByType(new Observation().getResourceType().name(), resources).size());

        Observation dateOfRadiologyObs = getObsAsResult(report, resources, "Date of Radiology Order");
        assertNotNull(dateOfRadiologyObs);
        assertEquals(DateUtil.parseDate("2008-08-18"), ((DateTimeType) dateOfRadiologyObs.getValue()).getValue());

        Observation radiologyFindings = getObsAsResult(report, resources, "Radiology Order Findings");
        assertNotNull(radiologyFindings);
        assertEquals(1, radiologyFindings.getRelated().size());

        Observation probemDescriptionObs = ((Observation) TestFhirFeedHelper.getResourceByReference(radiologyFindings.getRelatedFirstRep().getTarget(), resources).getResource());
        assertNotNull(probemDescriptionObs);
        assertEquals("Findings", ((StringType) probemDescriptionObs.getValue()).getValue());
    }

    private Observation getObsAsResult(DiagnosticReport report, List<FHIRResource> resources, String display) {
        for (Reference resourceReferenceDt : report.getResult()) {
            Observation observation = (Observation) TestFhirFeedHelper.getResourceByReference(resourceReferenceDt, resources).getResource();
            if (MapperTestHelper.containsCoding(observation.getCode().getCoding(), null, null, display))
                return observation;
        }
        return null;
    }

    private FHIREncounter createFhirEncounter() {
        Encounter encounter = new Encounter();
        encounter.setSubject(new Reference(patientRef));
        encounter.setId(fhirEncounterId);
        return new FHIREncounter(encounter);
    }
}