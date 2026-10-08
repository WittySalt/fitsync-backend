package com.fitsync.auth.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fitsync.outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        int batchSize,
        long fixedDelayMs
) {

    public OutboxRelayProperties {
        if (batchSize < 1 || batchSize > 100) {
            batchSize = 50;
        }
        if (fixedDelayMs < 1000L) {
            fixedDelayMs = 30000L;
        }
    }
}
