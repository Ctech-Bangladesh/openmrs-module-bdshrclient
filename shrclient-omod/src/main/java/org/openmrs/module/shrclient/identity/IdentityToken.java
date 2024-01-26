package org.openmrs.module.shrclient.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IdentityToken {
    public IdentityToken() {
    }

    public IdentityToken(String token) {
        this.token = token;
    }

    @JsonProperty("access_token")
    private String token;

    @Override
    public String toString() {
        return token;
    }
}
