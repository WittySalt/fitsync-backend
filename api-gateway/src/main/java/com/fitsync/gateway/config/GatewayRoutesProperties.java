package com.fitsync.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fitsync.gateway.routes")
public record GatewayRoutesProperties(
        @NotBlank String authServiceBaseUrl,
        @NotBlank String userServiceBaseUrl,
        @NotBlank String workoutServiceBaseUrl,
        @NotBlank String nutritionServiceBaseUrl,
        @NotBlank String analyticsServiceBaseUrl,
        @NotBlank String recommendationServiceBaseUrl,
        @NotBlank String notificationServiceBaseUrl) {
}
