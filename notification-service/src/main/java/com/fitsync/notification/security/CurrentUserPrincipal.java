package com.fitsync.notification.security;

import java.security.Principal;
import java.util.UUID;

/** The caller as the access token names them. Only the id and role are used here. */
public record CurrentUserPrincipal(UUID userId, String role) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
