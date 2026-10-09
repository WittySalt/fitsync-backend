package com.fitsync.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The secret shared with auth-service for verifying its access tokens. */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret) {
}
