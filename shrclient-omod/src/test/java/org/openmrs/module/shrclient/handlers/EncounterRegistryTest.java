package org.openmrs.module.shrclient.handlers;

import org.hl7.fhir.instance.model.AtomFeed;
import org.ict4h.atomfeed.client.domain.Event;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.api.UserService;
import org.openmrs.module.fhir.mapper.bundler.CompositionBundleCreator;
import org.openmrs.module.fhir.utils.Constants;
import org.openmrs.module.shrclient.dao.IdMappingsRepository;
import org.openmrs.module.shrclient.model.IdMapping;
import org.openmrs.module.shrclient.util.SHRClient;
import org.openmrs.module.shrclient.util.PropertiesReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EncounterRegistryTest {

    @Mock
    private EncounterService encounterService;

    @Mock
    private SHRClient shrClient;

    @Mock
    private PropertiesReader propertiesReader;

    @Mock
    private UserService userService;

    @Mock
    private CompositionBundleCreator compositionBundleCreator;

    @Mock
    private IdMappingsRepository idMappingsRepository;

    @Mock
    private ServiceClientRegistry serviceClientRegistry;

    private EncounterRegistry encounterRegistry;

    @Before
    public void setup() {
        initMocks(this);
        when(serviceClientRegistry.getSHRClient()).thenReturn(shrClient);
        encounterRegistry = new EncounterRegistry(
                encounterService, userService,
                propertiesReader, compositionBundleCreator,
                idMappingsRepository,
                serviceClientRegistry);
    }

    @Test
    public void shouldProcessEncounterSyncEvent() throws IOException {
        final String uuid = "123abc456";
        final Event event = new Event("id100", "/openmrs/ws/rest/v1/encounter/" + uuid
                + "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))");
        org.openmrs.Encounter openMrsEncounter = getOpenMrsEncounter();
        final AtomFeed atomFeed = new AtomFeed();

        when(encounterService.getEncounterByUuid(uuid)).thenReturn(openMrsEncounter);
        when(userService.getUserByUsername(Constants.SHR_CLIENT_SYSTEM_NAME)).thenReturn(new User(2));
        when(shrClient.post(anyString(), eq(atomFeed))).thenReturn("{\"encounterId\":\"shr-uuid\"}");
        when(compositionBundleCreator.compose(openMrsEncounter)).thenReturn(atomFeed);
        when(idMappingsRepository.findByExternalId(anyString())).thenReturn(null);
        encounterRegistry.process(event);

        verify(encounterService).getEncounterByUuid(uuid);
        verify(shrClient).post(anyString(), eq(atomFeed));
        verify(idMappingsRepository).saveMapping(Matchers.<IdMapping>anyObject());
    }

    private org.openmrs.Encounter getOpenMrsEncounter() {
        org.openmrs.Encounter openMrsEncounter = new org.openmrs.Encounter();
        openMrsEncounter.setCreator(new User(1));
        final Patient patient = new Patient();
        openMrsEncounter.setPatient(patient);
        final PersonAttributeType personAttributeType = new PersonAttributeType();
        personAttributeType.setName(Constants.HEALTH_ID_ATTRIBUTE);
        patient.setAttributes(new HashSet<PersonAttribute>(Arrays.asList(new PersonAttribute(personAttributeType, "1234567890123"))));
        return openMrsEncounter;
    }

    @Test
    public void shouldGetEncounterUuidFromEventContent() {
        final String uuid = "123abc456";
        final String content = "/openmrs/ws/rest/v1/encounter/" + uuid +
                "?v=custom:(uuid,encounterType,patient,visit,orders:(uuid,orderType,concept,voided))";
        assertEquals(uuid, encounterRegistry.getUuid(content));
    }

}
