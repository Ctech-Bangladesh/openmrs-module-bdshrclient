package org.openmrs.module.fhir.mapper.bundler;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import org.junit.After;
import org.junit.Test;
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.fhir.mapper.model.FHIREncounter;
import org.openmrs.module.fhir.mapper.model.FHIRResource;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.function.Predicate;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.junit.Assert.*;
import static org.openmrs.module.fhir.MapperTestHelper.getSystemProperties;

@org.springframework.test.context.ContextConfiguration(locations = {
		"classpath:TestingApplicationContext.xml" }, inheritLocations = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ObservationMapperIT extends BaseModuleWebContextSensitiveTest {

	@Autowired
	private ObsService obsService;

	@Autowired
	private ObservationMapper observationMapper;

	private String prUrl = "http://pr.com/23.json";

	private String prDisplay = "Doc 23";

	@After
	public void tearDown() throws Exception {
		deleteAllData();
	}

	@Test
	public void shouldMapObservations() throws Exception {
		executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
		Obs vitalsObs = obsService.getObs(11);
		assertTrue(observationMapper.canHandle(vitalsObs));
	}

	@Test
	public void shouldNotMapProcedureFulfillment() throws Exception {
		executeDataSet("testDataSets/procedureFulfillmentDS.xml");
		Obs fulfilmentObs = obsService.getObs(1011);
		assertFalse(observationMapper.canHandle(fulfilmentObs));
	}

	@Test
	public void shouldNotHandleRadiologyFulfillment() throws Exception {
		executeDataSet("testDataSets/radiologyFulfillmentDS.xml");
		Obs observation = obsService.getObs(501);
		assertFalse(observationMapper.canHandle(observation));
	}

	@Test
	public void shouldMapOMRSObsToFhirObservation() throws Exception {
		executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
		Obs vitalsObs = obsService.getObs(11);

		Encounter fhirEncounter = new Encounter();
		fhirEncounter.addParticipant().setIndividual(new ResourceReferenceDt().setReference(prUrl).setDisplay(prDisplay));
		List<FHIRResource> FHIRResources = observationMapper
				.map(vitalsObs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
		assertEquals(5, FHIRResources.size());
		for (FHIRResource FHIRResource : FHIRResources) {
			Observation observation = (Observation) FHIRResource.getResource();
			assertTrue(isNotEmpty(observation.getCode().getCoding()));
			assertObservation(observation);
			if (FHIRResource.getResourceName().equals("Vitals")) {
				assertVitalsObservation(observation, 2);
			} else if (FHIRResource.getResourceName().equals("Blood Pressure")) {
				assertBloodPressureObservation(observation, 2);
			} else if (FHIRResource.getResourceName().equals("Diastolic")) {
				assertDiastolicObservation(observation);
			} else if (FHIRResource.getResourceName().equals("Pulse")) {
				assertPulseObservation(observation);
			}
		}
	}

	@Test
	public void shouldNotMapAllIgnoredObservation() throws Exception {
		executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
		executeDataSet("testDataSets/globalProperties/ignoreConceptListForDiastolicAndPulse.xml");
		int numberOfObsAsserted = 0;
		Obs vitalsObs = obsService.getObs(11);

		Encounter fhirEncounter = new Encounter();
		fhirEncounter.addParticipant().setIndividual(new ResourceReferenceDt().setReference(prUrl).setDisplay(prDisplay));
		List<FHIRResource> FHIRResources = observationMapper
				.map(vitalsObs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
		assertEquals(3, FHIRResources.size());
		for (FHIRResource FHIRResource : FHIRResources) {
			Observation observation = (Observation) FHIRResource.getResource();
			assertTrue(isNotEmpty(observation.getCode().getCoding()));
			assertObservation(observation);
			if (FHIRResource.getResourceName().equals("Vitals")) {
				assertVitalsObservation(observation, 1);
				numberOfObsAsserted++;
			} else if (FHIRResource.getResourceName().equals("Blood Pressure")) {
				assertBloodPressureObservation(observation, 1);
				numberOfObsAsserted++;
			}
		}
		assertEquals(2, numberOfObsAsserted);
	}

	@Test
	public void shouldNotMapAnIgnoredObservationAndItsChildren() throws Exception {
		executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
		executeDataSet("testDataSets/globalProperties/ignoreConceptListForBP.xml");
		int numberOfObsAsserted = 0;
		Obs vitalsObs = obsService.getObs(11);

		Encounter fhirEncounter = new Encounter();
		fhirEncounter.addParticipant().setIndividual(new ResourceReferenceDt().setReference(prUrl).setDisplay(prDisplay));
		List<FHIRResource> FHIRResources = observationMapper
				.map(vitalsObs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
		assertEquals(2, FHIRResources.size());
		for (FHIRResource FHIRResource : FHIRResources) {
			Observation observation = (Observation) FHIRResource.getResource();
			assertTrue(isNotEmpty(observation.getCode().getCoding()));
			assertObservation(observation);
			if (FHIRResource.getResourceName().equals("Vitals")) {
				assertVitalsObservation(observation, 1);
				numberOfObsAsserted++;
			} else if (FHIRResource.getResourceName().equals("Pulse")) {
				assertPulseObservation(observation);
				numberOfObsAsserted++;
			}
		}
		assertEquals(2, numberOfObsAsserted);
	}

	@Test
	public void shouldNotMapRootLevelIgnoredObservations() throws Exception {
		executeDataSet("testDataSets/shrClientObservationsTestDs.xml");
		executeDataSet("testDataSets/globalProperties/ignoreConceptListForVitals.xml");
		Obs vitalsObs = obsService.getObs(11);

		Encounter fhirEncounter = new Encounter();
		fhirEncounter.addParticipant().setIndividual(new ResourceReferenceDt().setReference(prUrl).setDisplay(prDisplay));
		List<FHIRResource> FHIRResources = observationMapper
				.map(vitalsObs, new FHIREncounter(fhirEncounter), getSystemProperties("1"));
		assertTrue(isEmpty(FHIRResources));
	}

	@Test
	public void shouldNotMapAnyObservationWhenAllTheLeafObsHaveIgnoredConcepts() throws Exception {
		executeDataSet("testDataSets/encounterWithObsHavingIgnoredLeafLevelChildren.xml");

		Obs vitalsObs = obsService.getObs(111);
		Encounter encounter = new Encounter();
		List<FHIRResource> fhirResources = observationMapper.map(vitalsObs, new FHIREncounter(encounter), getSystemProperties("1"));

		assertTrue(isEmpty(fhirResources));
	}

	@Test
	public void shouldNotMapAnObsHierarchyWhenLeafObsHaveIgnoredConcepts() throws Exception {
		executeDataSet("testDataSets/encounterWithObsHavingIgnoredLeafLevelChildren.xml");

		Obs vitalsObs = obsService.getObs(114);
		Encounter encounter = new Encounter();
		List<FHIRResource> fhirResources = observationMapper.map(vitalsObs, new FHIREncounter(encounter), getSystemProperties("1"));

		assertEquals(2, fhirResources.size());
		Observation vitals = (Observation)fhirResources.stream().filter(
				fhirResource -> fhirResource.getResourceName().equals("Vitals")
		).findFirst().get().getResource();
		assertTrue(isNotEmpty(vitals.getCode().getCoding()));
		assertEquals(1, vitals.getRelated().size());

		Observation temperature = (Observation)fhirResources.stream().filter(
				fhirResource -> fhirResource.getResourceName().equals("Temperature")
		).findFirst().get().getResource();
		assertTrue(isNotEmpty(temperature.getCode().getCoding()));
		assertEquals(0, temperature.getRelated().size());
	}

	private void assertObservation(Observation observation) {
		assertEquals(1, observation.getPerformer().size());
		assertEquals(prUrl, observation.getPerformer().get(0).getReference().getValue());
		assertEquals(prDisplay, observation.getPerformer().get(0).getDisplay().getValue());

		assertEquals(ObservationStatusEnum.PRELIMINARY, observation.getStatusElement().getValueAsEnum());
	}

	private void assertPulseObservation(Observation observation) {
		List<CodingDt> coding = observation.getCode().getCoding();
		assertEquals(2, coding.size());
		int flag = 0;
		for (CodingDt code : coding) {
			if (assertCoding(code, "103", "/concepts/103"))
				flag++;
			if (assertCoding(code, "M54.418965", "referenceterms/201"))
				flag++;
		}
		assertEquals(2, flag);
		assertTrue(133 == ((QuantityDt) observation.getValue()).getValue().doubleValue());
		assertTrue(isEmpty(observation.getRelated()));
	}

	private void assertDiastolicObservation(Observation observation) {
		List<CodingDt> coding = observation.getCode().getCoding();
		assertEquals(1, coding.size());
		assertTrue(assertCoding(coding.get(0), "105", "/concepts/105"));
		assertTrue(120 == ((QuantityDt) observation.getValue()).getValue().doubleValue());
		assertTrue(isEmpty(observation.getRelated()));
	}

	private void assertBloodPressureObservation(Observation observation, int expectedRelatedSize) {
		List<CodingDt> coding = observation.getCode().getCoding();
		assertEquals(1, coding.size());
		assertTrue(null == coding.get(0).getCode());
		assertTrue(null == coding.get(0).getSystem());
		assertEquals("Blood Pressure", coding.get(0).getDisplay());
		assertEquals(expectedRelatedSize, observation.getRelated().size());
	}

	private void assertVitalsObservation(Observation observation, int expectedRelatedSize) {
		List<CodingDt> coding = observation.getCode().getCoding();
		assertEquals(1, coding.size());
		assertTrue(assertCoding(coding.get(0), "101", "/concepts/101"));
		assertEquals(expectedRelatedSize, observation.getRelated().size());
	}

	private boolean assertCoding(CodingDt code, String expectedCode, String expectedSystem) {
		return ((expectedCode.equals(code.getCode())) && (expectedSystem.equals(code.getSystem())));
	}
}
