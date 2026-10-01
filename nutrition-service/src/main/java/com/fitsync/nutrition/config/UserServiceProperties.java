package com.fitsync.nutrition.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the person's sex, age, weight and dietary pattern are read from. user-service owns them, so the
 * Diary asks for them at request time with the caller's own token instead of keeping a copy. The timeouts
 * are short because a slow user-service must cost the page a fallback, not a wait.
 *
 * @param enabled        false switches the lookups off, which tests and local runs without user-service use
 * @param baseUrl        root of user-service, without a trailing slash
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout    how long to wait for an answer
 */
@ConfigurationProperties(prefix = "fitsync.nutrition.user-service")
public record UserServiceProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public UserServiceProperties {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = Duration.ofMillis(800);
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = Duration.ofMillis(1500);
        }
    }
}
