package com.fitsync.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fitsync.gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins) {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:3000",
            "http://localhost:4200",
            "https://d26vi6hikexzev.cloudfront.net",
            "https://fitsyncapp.click",
            "https://www.fitsyncapp.click");

    public GatewayCorsProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? DEFAULT_ALLOWED_ORIGINS
                : List.copyOf(allowedOrigins);
    }

    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }
}
