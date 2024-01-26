package org.openmrs.module.shrclient.util;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.module.fhir.utils.GlobalPropertyLookUpService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SystemUserServiceTest {
    @Mock
    private UserService userService;
    @Mock
    private GlobalPropertyLookUpService globalPropertyLookUpService;
    private SystemUserService systemUserService;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        systemUserService = new SystemUserService(userService, null, globalPropertyLookUpService);
    }

    @Test
    public void shouldCheckIfOpenMrsDaemonUserHasUpdated() throws Exception {
        final org.openmrs.Patient openMrsPatient = new org.openmrs.Patient();
        User shrUser = new User();
        int userId = 2;
        shrUser.setId(userId);

        openMrsPatient.setCreator(shrUser);
        when(globalPropertyLookUpService.getGlobalPropertyValue("shr.system.user")).thenReturn("2");
        when(userService.getUser(userId)).thenReturn(shrUser);
        assertTrue(systemUserService.isUpdatedByOpenMRSShrSystemUser(openMrsPatient));

        User bahmniUser = new User();
        bahmniUser.setId(1);
        openMrsPatient.setCreator(bahmniUser);
        assertFalse(systemUserService.isUpdatedByOpenMRSShrSystemUser(openMrsPatient));
    }
}