package com.fitsync.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.cognito")
public record CognitoProperties(
        boolean enabled,
        String issuerUri,
        String jwkSetUri,
        String audience,
        String principalClaim) {
}
