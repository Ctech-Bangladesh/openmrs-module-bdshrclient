package org.openmrs.module.shrclient.identity;

import org.springframework.stereotype.Component;

@Component("bdshrIdentityStore")
public class IdentityStore {
    IdentityToken token;

    public IdentityToken getToken() {
        return token;
    }

    public void setToken(IdentityToken token) {
        this.token = token;
    }
}