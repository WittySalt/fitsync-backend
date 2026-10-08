package com.fitsync.analytics.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the outbox relay. Out-of-range values fall back to the defaults (50 events per batch, a 30
 * second delay).
 */
@ConfigurationProperties(prefix = "fitsync.outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        int batchSize,
        long fixedDelayMs) {

    public OutboxRelayProperties {
        if (batchSize < 1 || batchSize > 100) {
            batchSize = 50;
        }
        if (fixedDelayMs < 1000L) {
            fixedDelayMs = 30000L;
        }
    }
}
