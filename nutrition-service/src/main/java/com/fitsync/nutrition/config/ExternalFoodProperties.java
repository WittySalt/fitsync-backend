package com.fitsync.nutrition.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NU-8: external food source (OpenFoodFacts) fallback. Disabled by default — enable only where the
 * service is allowed outbound HTTP.
 */
@ConfigurationProperties(prefix = "fitsync.nutrition.external-food")
public record ExternalFoodProperties(
        boolean enabled,
        String baseUrl,
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        Duration minRequestInterval) {

    public ExternalFoodProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://world.openfoodfacts.org";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "FitSync/1.0 (nutrition-service)";
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = Duration.ofSeconds(3);
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            minRequestInterval = Duration.ofMillis(250);
        }
    }
}
