package org.openmrs.module.shrclient.identity;

import org.springframework.stereotype.Component;

@Component("bdshrIdentityStore")
public class IdentityStore {
    private IdentityToken token;

    public IdentityToken getToken() {
        return token;
    }

    public void setToken(IdentityToken token) {
        this.token = token;
    }

    public void clearToken() {
        this.token = null;
    }
}
