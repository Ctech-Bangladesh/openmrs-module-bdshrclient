//package org.openmrs.module.fhir.mapper.emr;
//
//import org.hl7.fhir.dstu3.model.Bundle;
//import org.junit.Before;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.openmrs.VisitType;
//import org.openmrs.api.VisitService;
//import org.openmrs.module.fhir.FhirContextHelper;
//import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
//import org.openmrs.module.fhir.utils.FHIRBundleHelper;
//
//import java.io.IOException;
//import java.io.InputStream;
//
//import static java.util.Arrays.asList;
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertNull;
//import static org.mockito.Mockito.when;
//import static org.mockito.MockitoAnnotations.initMocks;
//
//public class FHIRMapperTest {
//    @Mock
//    private VisitService mockVisitService;
//
//    private FHIRMapper fhirMapper;
//
//    @Before
//    public void setUp() throws Exception {
//        initMocks(this);
//        fhirMapper = new FHIRMapper(null, null, null, null, mockVisitService);
//    }
//
//    @Test
//    public void shouldMapAmbulatoryVisitType() throws Exception {
//        VisitType visitType = new VisitType("ambulatory", "ambulatory");
//        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
//
//        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(EncounterClass.AMBULATORY);
//        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle);
//        assertEquals(visitType, actualVisitType);
//    }
//
//    @Test
//    public void shouldMapOPDVisitType() throws Exception {
//        VisitType visitType = new VisitType("OPD", "OPD");
//        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
//        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(EncounterClassEnum.OUTPATIENT);
//        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle);
//        assertEquals(visitType, actualVisitType);
//    }
//
//    @Test
//    public void shouldMapIPDVisitType() throws Exception {
//        VisitType visitType = new VisitType("IPD", "IPD");
//        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
//        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(EncounterClassEnum.INPATIENT);
//        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle);
//        assertEquals(visitType, actualVisitType);
//    }
//
//    @Test
//    public void shouldReturnNullIfVisitTypeNotFound() throws Exception {
//        VisitType visitType = new VisitType("IPD-1", "IPD-1");
//        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
//        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(EncounterClassEnum.INPATIENT);
//        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle);
//        assertNull(actualVisitType);
//    }
//
//    private ShrEncounterBundle createEncounterBundleWithClass(EncounterClass encounterClass) throws IOException {
//        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("encounterBundles/dstu2/testFHIREncounter.xml");
//        String bundleXML = org.apache.commons.io.IOUtils.toString(inputStream);
//        Bundle bundle = (Bundle) FhirContextHelper.getFhirContext().newXmlParser().parseResource(bundleXML);
//        FHIRBundleHelper.getEncounter(bundle).setClassElement(encounterClass);
//        return new ShrEncounterBundle(bundle, null, null);
//    }
//}