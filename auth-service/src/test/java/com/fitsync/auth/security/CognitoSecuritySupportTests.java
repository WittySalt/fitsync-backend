package com.fitsync.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoSecuritySupportTests {

    @Test
    void converterCombinesJwtScopesCognitoGroupsAndRoleClaim() {
        Jwt jwt = jwtBuilder()
                .claim("scope", "profile email")
                .claim("cognito:groups", List.of("admin", "ROLE_MANAGER", " ", 42))
                .claim("role", "coach")
                .build();

        assertThat(new CognitoAuthoritiesConverter().convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(
                        "SCOPE_profile",
                        "SCOPE_email",
                        "ROLE_ADMIN",
                        "ROLE_MANAGER",
                        "ROLE_COACH");
    }

    @Test
    void converterPreservesPrefixedRoleClaimAndIgnoresMissingGroups() {
        Jwt jwt = jwtBuilder()
                .claim("role", "ROLE_SUPPORT")
                .build();

        assertThat(new CognitoAuthoritiesConverter().convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_SUPPORT");
    }

    @Test
    void audienceValidatorSucceedsWhenAudienceIsBlankOrPresent() {
        Jwt jwt = jwtBuilder()
                .audience(List.of("fitsync-api"))
                .build();

        assertThat(new AudienceValidator(" ").validate(jwt).hasErrors()).isFalse();
        assertThat(new AudienceValidator("fitsync-api").validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void audienceValidatorFailsWhenConfiguredAudienceIsMissing() {
        Jwt jwt = jwtBuilder()
                .audience(List.of("other-api"))
                .build();

        var result = new AudienceValidator("fitsync-api").validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .extracting(error -> error.getErrorCode())
                .containsExactly("invalid_token");
    }

    private static Jwt.Builder jwtBuilder() {
        Instant issuedAt = Instant.parse("2026-06-21T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300));
    }
}
