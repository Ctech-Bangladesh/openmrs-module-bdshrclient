package org.openmrs.module.shrclient.mapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.*;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ServiceContext;
import org.openmrs.module.addresshierarchy.AddressField;
import org.openmrs.module.addresshierarchy.AddressHierarchyEntry;
import org.openmrs.module.addresshierarchy.AddressHierarchyLevel;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.fhir.utils.DateUtil;
import org.openmrs.module.shrclient.dao.IdMappingRepository;
import org.openmrs.module.shrclient.model.Address;
import org.openmrs.module.shrclient.model.Patient;
import org.openmrs.module.shrclient.model.Relation;
import org.openmrs.module.shrclient.model.Status;
import org.openmrs.module.shrclient.service.BbsCodeService;
import org.openmrs.module.shrclient.util.AddressHelper;
import org.openmrs.module.shrclient.util.SystemProperties;

import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openmrs.module.fhir.OpenMRSConstants.*;

public class PatientMapperTest {

    @Mock
    private AddressHierarchyService addressHierarchyService;
    @Mock
    private SystemProperties systemProperties;
    @Mock
    private IdMappingRepository idMappingRepository;
    @Mock
    private PersonService personService;

    private AddressHelper addressHelper;
    private BbsCodeService bbsCodeService;

    private PatientMapper patientMapper;
    private String nationalId = "nid-100";
    private String healthId = "hid-200";
    private String brnId = "brn-200";
    private String occupation = "agriculture";
    private String educationLevel = "6th to 9th";

    private String houseHoldCode = "house4";
    private org.openmrs.Patient openMrsPatient;
    private Patient patient;
    private PersonAddress address;

    @Before
    public void setup() throws Exception {
        initMocks(this);
        this.bbsCodeService = new BbsCodeService();
        addressHelper = new AddressHelper(addressHierarchyService);
        patientMapper = new PatientMapper(bbsCodeService, addressHelper, idMappingRepository);
        setUpAddressHierarchy();
        setupData();

        mockPersonServiceInContext();
    }

    private void mockPersonServiceInContext() {
        Context context = new Context();
        ServiceContext serviceContext = ServiceContext.getInstance();
        serviceContext.setService(PersonService.class, personService);
        PersonAttributeType attributeType = new PersonAttributeType();
        when(personService.getPersonAttributeTypeByName(anyString())).thenReturn(attributeType);
        context.setServiceContext(serviceContext);
    }

    @Test
    public void shouldMapOpenMrsPatientToMciPatient() throws Exception {
        Patient expectedPatient = patientMapper.map(openMrsPatient);
        assertEquals(this.patient, expectedPatient);
    }

    @Test
    public void shouldMapDobTypeAsEstimated() throws Exception {
        openMrsPatient.setBirthdateEstimated(Boolean.TRUE);
        patient.setDobType("3");
        Patient expectedPatient = patientMapper.map(openMrsPatient);
        assertEquals(this.patient, expectedPatient);
    }

    @Test
    public void shouldMapRelationsWhenPresent() throws Exception {
        PersonAttribute fatherAttribute = createAttribute(FATHER_NAME_ATTRIBUTE_TYPE, "Oh My Daddy");
        openMrsPatient.getAttributes().add(fatherAttribute);
        PersonAttribute motherAttribute = createAttribute(MOTHER_NAME_ATTRIBUTE_TYPE, "My Mother");
        openMrsPatient.getAttributes().add(motherAttribute);
        PersonAttribute spouseAttribute = createAttribute(SPOUSE_NAME_ATTRIBUTE_TYPE, "OhMyDear");
        openMrsPatient.getAttributes().add(spouseAttribute);

        Patient mappedPatient = patientMapper.map(openMrsPatient);
        org.openmrs.module.shrclient.model.Relation[] mappedRelations = mappedPatient.getRelations();

        assertEquals(3, mappedRelations.length);
        Relation fatherRelation = getRelationByType(mappedRelations, "FTH");
        assertEquals("Oh My", fatherRelation.getGivenName());
        assertEquals("Daddy", fatherRelation.getSurName());

        Relation motherRelation = getRelationByType(mappedRelations, "MTH");
        assertEquals("My", motherRelation.getGivenName());
        assertEquals("Mother", motherRelation.getSurName());

        Relation spouseRelation = getRelationByType(mappedRelations, "SPS");
        assertEquals("OhMyDear", spouseRelation.getGivenName());
        assertNull(spouseRelation.getSurName());
    }

    @Test
    public void shouldNotMapRelationsWhenNotPresent() throws Exception {
        Patient orphanPatient = patientMapper.map(openMrsPatient);
        assertTrue(CollectionUtils.isEmpty(filterRelationsWithNames(orphanPatient.getRelations())));
    }

    @Test
    public void shouldMapHidCardStatusAsRegistered() throws Exception {
        openMrsPatient.addAttribute(createAttribute(HID_CARD_ISSUED_ATTRIBUTE, "false"));
        assertEquals("REGISTERED", patientMapper.map(openMrsPatient).getHidCardStatus());
    }

    @Test
    public void shouldMapHidCardStatusAsISSUED() throws Exception {
        openMrsPatient.addAttribute(createAttribute(HID_CARD_ISSUED_ATTRIBUTE, "true"));
        assertEquals("ISSUED", patientMapper.map(openMrsPatient).getHidCardStatus());
    }

    @Test
    public void shouldNotMapHidCardStatus() throws Exception {
        assertNull(patientMapper.map(openMrsPatient).getHidCardStatus());
    }

    private List<Relation> filterRelationsWithNames(Relation[] relations) {
        List<Relation> relationsWithName = new ArrayList<>();
        for (Relation relation : relations) {
            if (StringUtils.isNotBlank(relation.getGivenName()) || StringUtils.isNotBlank(relation.getSurName()))
                relationsWithName.add(relation);
        }
        return relationsWithName;
    }

    private Relation getRelationByType(Relation[] mappedRelations, String type) {
        for (Relation mappedRelation : mappedRelations) {
            if (type.equals(mappedRelation.getType())) return mappedRelation;
        }
        return null;
    }

    private void setupData() throws ParseException {
        final String givenName = "Sachin";
        final String middleName = "Ramesh";
        final String familyName = "Tendulkar";
        final String gender = "M";
        final Date dateOfBirth = DateUtil.parseDate("2000-12-31", DateUtil.SIMPLE_DATE_FORMAT);
        final Date dateOfDeath = DateUtil.parseDate("2010-12-31", DateUtil.SIMPLE_DATE_FORMAT);

        Person person = new Person();

        PersonName personName = new PersonName(givenName, middleName, familyName);
        person.addName(personName);
        person.setGender(gender);
        person.setBirthdate(dateOfBirth);
        person.setDeathDate(dateOfDeath);
        person.setDead(true);
        person.addAddress(this.address);
        person.setBirthdateEstimated(Boolean.FALSE);
        openMrsPatient = new org.openmrs.Patient(person);
        PatientIdentifierType patientIdentifierType = new PatientIdentifierType();
        patientIdentifierType.setName(HEALTH_ID_IDENTIFIER_TYPE_NAME);
        openMrsPatient.addIdentifier(new PatientIdentifier(healthId, patientIdentifierType, new Location(1)));
        openMrsPatient.setAttributes(createOpenMrsPersonAttributes());

        patient = new Patient();
        patient.setNationalId(nationalId);
        patient.setHealthId(healthId);
        patient.setBirthRegNumber(brnId);
        patient.setHouseHoldCode(houseHoldCode);
        patient.setGivenName(givenName);
        patient.setSurName(familyName);
        patient.setGender(gender);
        patient.setDateOfBirth(dateOfBirth);
        patient.setOccupation(bbsCodeService.getOccupationCode(occupation));
        patient.setEducationLevel(bbsCodeService.getEducationCode(educationLevel));

        Status status = new Status();
        status.setType('2');
        status.setDateOfDeath(dateOfDeath);
        patient.setDobType("1");
        patient.setStatus(status);


        Address a = new Address();
        a.setAddressLine("Address line");
        a.setDivisionId("10");
        a.setDistrictId("20");
        a.setUpazilaId("30");
        a.setCityCorporationId("40");
        a.setUnionOrUrbanWardId("50");
        a.setRuralWardId("01");
        patient.setAddress(a);
    }

    private void setUpAddressHierarchy() {
        String divisionId = "10";
        String districtId = "1020";
        String upazillaId = "102030";
        String cityCorpId = "10203040";
        String unionOrUrbanWardId = "1020304050";
        String ruralWardId = "102030405001";

        String division = "some-division";
        String district = "some-district";
        String upazilla = "some-upazilla";
        String cityCorp = "some-cityCorp";
        String unionOrUrbanWard = "some-urban-ward";
        String ruralWard = "some-rural-ward";

        List<AddressHierarchyEntry> divisionEntries = createAddressHierarchyEntries(divisionId);
        List<AddressHierarchyEntry> districtEntries = createAddressHierarchyEntries(districtId);
        List<AddressHierarchyEntry> upazillaEntries = createAddressHierarchyEntries(upazillaId);
        List<AddressHierarchyEntry> cityCorpEntries = createAddressHierarchyEntries(cityCorpId);
        List<AddressHierarchyEntry> unionOrUrbanWardEntries = createAddressHierarchyEntries(unionOrUrbanWardId);
        List<AddressHierarchyEntry> ruralWardEntries = createAddressHierarchyEntries(ruralWardId);

        when(addressHierarchyService.getAddressHierarchyLevelByAddressField(any(AddressField.class))).thenReturn(new AddressHierarchyLevel());
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndName(any(AddressHierarchyLevel.class), eq(division))).thenReturn(divisionEntries);
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndNameAndParent(any(AddressHierarchyLevel.class), eq(district), any(AddressHierarchyEntry.class))).thenReturn(districtEntries);
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndNameAndParent(any(AddressHierarchyLevel.class), eq(upazilla), any(AddressHierarchyEntry.class))).thenReturn(upazillaEntries);
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndNameAndParent(any(AddressHierarchyLevel.class), eq(cityCorp), any(AddressHierarchyEntry.class))).thenReturn(cityCorpEntries);
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndNameAndParent(any(AddressHierarchyLevel.class), eq(unionOrUrbanWard), any(AddressHierarchyEntry.class))).thenReturn(unionOrUrbanWardEntries);
        when(addressHierarchyService.getAddressHierarchyEntriesByLevelAndNameAndParent(any(AddressHierarchyLevel.class), eq(ruralWard), any(AddressHierarchyEntry.class))).thenReturn(ruralWardEntries);

        PersonAddress address = new PersonAddress();
        address.setAddress1("Address line");
        address.setStateProvince(division);
        address.setCountyDistrict(district);
        address.setAddress5(upazilla);
        address.setAddress4(cityCorp);
        address.setAddress3(unionOrUrbanWard);
        address.setAddress2(ruralWard);

        this.address = address;
    }

    private Set<PersonAttribute> createOpenMrsPersonAttributes() {
        Set<PersonAttribute> attributes = new HashSet<>();
        attributes.add(createAttribute(NATIONAL_ID_ATTRIBUTE, nationalId));
        attributes.add(createAttribute(HEALTH_ID_ATTRIBUTE, healthId));
        attributes.add(createAttribute(BIRTH_REG_NO_ATTRIBUTE, brnId));
        attributes.add(createAttribute(OCCUPATION_ATTRIBUTE, occupation));
        attributes.add(createAttribute(EDUCATION_ATTRIBUTE, educationLevel));
        attributes.add(createAttribute(HOUSE_HOLD_CODE_ATTRIBUTE, houseHoldCode));
        return attributes;
    }

    private PersonAttribute createAttribute(String attributeName, String attributeValue) {
        PersonAttributeType attributeType = new PersonAttributeType();
        attributeType.setName(attributeName);
        return new PersonAttribute(attributeType, attributeValue);
    }

    private List<AddressHierarchyEntry> createAddressHierarchyEntries(String id) {
        List<AddressHierarchyEntry> entries1 = new ArrayList<>();
        AddressHierarchyEntry entry1 = new AddressHierarchyEntry();
        entry1.setUserGeneratedId(id);
        entries1.add(entry1);
        return entries1;
    }
}
