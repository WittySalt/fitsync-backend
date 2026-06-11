package com.fitsync.workout;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public final class TestJwt {

    private TestJwt() {
    }

    public static UsernamePasswordAuthenticationToken authentication(String role) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                role.toLowerCase() + "@example.com",
                role,
                role);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + role));
    }
}
