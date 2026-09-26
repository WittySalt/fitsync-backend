package com.fitsync.recommendation.coach.controller;

import java.util.UUID;

import com.fitsync.recommendation.security.CurrentUserPrincipal;
import org.springframework.security.core.Authentication;

/**
 * How a coach endpoint gets the caller's identity and token.
 *
 * <p>Shared because both coach controllers need the same two things, and because the second of them
 * is easy to get subtly wrong. The raw bearer token is forwarded to other services so the coach
 * reads with exactly the caller's authority, and it is taken from the header rather than rebuilt
 * from the security context, since the work happens off the request thread.
 */
final class CoachPrincipal {

    private CoachPrincipal() {
    }

    static UUID userId(Authentication authentication) {
        return ((CurrentUserPrincipal) authentication.getPrincipal()).userId();
    }

    static String bearerToken(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
    }
}
