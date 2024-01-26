package org.openmrs.module.shrclient.identity;

import org.openmrs.module.fhir.utils.PropertyKeyConstants;
import org.openmrs.module.shrclient.util.Headers;
import org.openmrs.module.shrclient.util.IdentityProviderClient;
import org.openmrs.module.shrclient.util.PropertiesReader;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class IdentityProviderService {
    private static final String EMAIL_KEY = "email";
    private static final String PASSWORD_KEY = "password";
    PropertiesReader propertiesReader;
    private IdentityStore identityStore;

    public IdentityProviderService(PropertiesReader propertiesReader, IdentityStore identityStore) {
        this.propertiesReader = propertiesReader;
        this.identityStore = identityStore;
    }

    private IdentityProviderClient getIdentityServiceClient(Properties facilityInstanceProperties) {
        HashMap<String, String> headers = Headers.getHrmIdentityHeaders(facilityInstanceProperties);
        return new IdentityProviderClient(propertiesReader.getIdPBaseUrl(), headers);
    }

    public IdentityToken getOrCreateToken() throws IdentityUnauthorizedException {
        IdentityToken identityToken = identityStore.getToken();
        if (identityToken == null) {
            Properties facilityInstanceProperties = propertiesReader.getFacilityInstanceProperties();
            Map<String, String> clientCredentials = getClientCredentials(facilityInstanceProperties);
            String url = propertiesReader.getIdPSignInPath();
            identityToken = getIdentityServiceClient(facilityInstanceProperties)
                    .post(url, clientCredentials, IdentityToken.class);
            identityStore.setToken(identityToken);
        }
        return identityToken;
    }

    private Map<String, String> getClientCredentials(Properties facilityInstanceProperties) {
        Map<String, String> clientCredentials = new HashMap<>();
        clientCredentials.put(EMAIL_KEY, facilityInstanceProperties.getProperty(PropertyKeyConstants.FACILITY_EMAIL_KEY));
        clientCredentials.put(PASSWORD_KEY, facilityInstanceProperties.getProperty(PropertyKeyConstants.FACILITY_PASSWORD_KEY));
        return clientCredentials;
    }
}
