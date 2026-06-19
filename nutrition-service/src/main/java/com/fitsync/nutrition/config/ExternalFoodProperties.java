package com.fitsync.nutrition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NU-8: external food source (OpenFoodFacts) fallback. Disabled by default — enable only where the
 * service is allowed outbound HTTP.
 */
@ConfigurationProperties(prefix = "fitsync.nutrition.external-food")
public record ExternalFoodProperties(
        boolean enabled,
        String baseUrl,
        String userAgent) {

    public ExternalFoodProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://world.openfoodfacts.org";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "FitSync/1.0 (nutrition-service)";
        }
    }
}
