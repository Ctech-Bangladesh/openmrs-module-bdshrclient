package org.openmrs.module.shrclient.handlers;

import org.openmrs.module.shrclient.identity.IdentityProviderService;
import org.openmrs.module.shrclient.identity.IdentityStore;
import org.openmrs.module.shrclient.identity.IdentityToken;
import org.openmrs.module.shrclient.identity.IdentityUnauthorizedException;
import org.openmrs.module.shrclient.util.Headers;
import org.openmrs.module.shrclient.util.PropertiesReader;
import org.openmrs.module.shrclient.util.RestClient;
import org.openmrs.module.shrclient.util.SHRClient;

import java.util.HashMap;
import java.util.Properties;


public class ClientRegistry {
    private PropertiesReader propertiesReader;
    private IdentityStore identityStore;

    public ClientRegistry(PropertiesReader propertiesReader, IdentityStore identityStore) {
        this.propertiesReader = propertiesReader;
        this.identityStore = identityStore;
    }

    public RestClient getMCIClient() throws IdentityUnauthorizedException {
        return new RestClient(propertiesReader.getMciBaseUrl(),
                Headers.getHrmAccessTokenHeaders(getOrCreateIdentityToken(), propertiesReader.getFacilityInstanceProperties()));
    }

    public SHRClient getSHRClient() throws IdentityUnauthorizedException {
        HashMap<String, String> headers = Headers.getHrmAccessTokenHeaders(getOrCreateIdentityToken(), propertiesReader.getFacilityInstanceProperties());
        return new SHRClient(propertiesReader.getShrBaseUrl(), headers);
    }

    public IdentityToken getOrCreateIdentityToken() throws IdentityUnauthorizedException {
        return new IdentityProviderService(propertiesReader, identityStore).getOrCreateToken();
    }

    public void clearIdentityToken() {
        identityStore.clearToken();
    }

    public RestClient getLRClient() throws IdentityUnauthorizedException {
        return getRestClient(propertiesReader.getLrBaseUrl());
    }

    public RestClient getFRClient() throws IdentityUnauthorizedException {
        return getRestClient(propertiesReader.getFrBaseUrl());
    }

    public RestClient getPRClient() throws IdentityUnauthorizedException {
        return getRestClient(propertiesReader.getPrBaseUrl());
    }

    private RestClient getRestClient(String baseUrl) throws IdentityUnauthorizedException {
        HashMap<String, String> headers = Headers.getHrmIdentityHeaders(propertiesReader.getFacilityInstanceProperties());
        return new RestClient(baseUrl, headers);
    }
}
