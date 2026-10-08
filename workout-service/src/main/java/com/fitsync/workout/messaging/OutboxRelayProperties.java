package com.fitsync.workout.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Relay settings under {@code fitsync.outbox.relay}. A batch size outside 1..100 falls back to 50 and an
 * interval shorter than a second (or missing) to 30 seconds, the same bounds the other services apply.
 */
@ConfigurationProperties(prefix = "fitsync.outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        Duration interval,
        int batchSize) {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    public OutboxRelayProperties {
        if (batchSize < 1 || batchSize > 100) {
            batchSize = 50;
        }
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            interval = Duration.ofSeconds(30);
        }
    }
}
