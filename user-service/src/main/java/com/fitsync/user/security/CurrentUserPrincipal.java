package com.fitsync.user.security;

import java.security.Principal;
import java.util.UUID;

public record CurrentUserPrincipal(
        UUID userId,
        String email,
        String displayName,
        String role) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
