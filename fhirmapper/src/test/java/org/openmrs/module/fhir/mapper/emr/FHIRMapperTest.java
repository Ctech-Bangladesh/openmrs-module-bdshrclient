package org.openmrs.module.fhir.mapper.emr;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Coding;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.VisitType;
import org.openmrs.api.VisitService;
import org.openmrs.module.fhir.FhirContextHelper;
import org.openmrs.module.fhir.MRSProperties;
import org.openmrs.module.fhir.mapper.model.ShrEncounterBundle;
import org.openmrs.module.fhir.utils.FHIRBundleHelper;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import static java.util.Arrays.asList;
import static org.hl7.fhir.dstu3.model.codesystems.V3ActCode.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class FHIRMapperTest {
    @Mock
    private VisitService mockVisitService;

    @Mock
    private SystemProperties systemProperties;

    private FHIRMapper fhirMapper;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        fhirMapper = new FHIRMapper(null, null, null, null, mockVisitService);
        when(systemProperties.getEncounterClassToVisitTypeMap()).thenReturn(getMockEncounterClassVisitTyoeMap());

    }

    @Test
    public void shouldMapFieldVisitType() throws Exception {
        VisitType visitType = new VisitType(MRSProperties.MRS_FIELD_VISIT_TYPE, "field");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));

        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(FLD.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertEquals(visitType, actualVisitType);
    }

    @Test
    public void shouldMapOPDVisitType() throws Exception {
        VisitType visitType = new VisitType(MRSProperties.MRS_OUTPATIENT_VISIT_TYPE, "OPD");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(AMB.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertEquals(visitType, actualVisitType);
    }

    @Test
    public void shouldMapIPDVisitType() throws Exception {
        VisitType visitType = new VisitType(MRSProperties.MRS_INPATIENT_VISIT_TYPE, "IPD");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(IMP.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertEquals(visitType, actualVisitType);
    }

    @Test
    public void shouldMapEmergengyVisitType() throws Exception {
        VisitType visitType = new VisitType(MRSProperties.MRS_EMERGENCY_VISIT_TYPE, "Emergency");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(EMER.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertEquals(visitType, actualVisitType);
    }

    @Test
    public void shouldMapHomeVisitType() throws Exception {
        VisitType visitType = new VisitType(MRSProperties.MRS_HOME_VISIT_TYPE, "Home");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(HH.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertEquals(visitType, actualVisitType);
    }

    @Test
    public void shouldReturnNullIfVisitTypeNotFound() throws Exception {
        VisitType visitType = new VisitType("IPD-1", "IPD-1");
        when(mockVisitService.getAllVisitTypes()).thenReturn(asList(visitType));
        ShrEncounterBundle shrEncounterBundle = createEncounterBundleWithClass(IMP.toCode());
        VisitType actualVisitType = fhirMapper.getVisitType(shrEncounterBundle, systemProperties);
        assertNull(actualVisitType);
    }

    private ShrEncounterBundle createEncounterBundleWithClass(String encounterClass) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("encounterBundles/stu3/testFHIREncounter.xml");
        String bundleXML = org.apache.commons.io.IOUtils.toString(inputStream);
        Bundle bundle = (Bundle) FhirContextHelper.getFhirContext().newXmlParser().parseResource(bundleXML);
        FHIRBundleHelper.getEncounter(bundle).setClass_(new Coding().setCode(encounterClass));
        return new ShrEncounterBundle(bundle, null, null);
    }

    private HashMap<String, String> getMockEncounterClassVisitTyoeMap() {
        HashMap<String, String> encounterClassToVisitTypeNameMap = new HashMap<>();
        encounterClassToVisitTypeNameMap.put(IMP.toCode(), MRSProperties.MRS_INPATIENT_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(FLD.toCode(), MRSProperties.MRS_FIELD_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(AMB.toCode(), MRSProperties.MRS_CARE_SETTING_FOR_OUTPATIENT);
        encounterClassToVisitTypeNameMap.put(HH.toCode(), MRSProperties.MRS_HOME_VISIT_TYPE);
        encounterClassToVisitTypeNameMap.put(EMER.toCode(), MRSProperties.MRS_EMERGENCY_VISIT_TYPE);
        return encounterClassToVisitTypeNameMap;
    }
}