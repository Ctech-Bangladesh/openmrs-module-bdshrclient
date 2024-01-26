package org.openmrs.module.shrclient.identity;

import java.io.IOException;

public class IdentityUnauthorizedException extends IOException {
    public IdentityUnauthorizedException(String message) {
        super(message);
    }

}
