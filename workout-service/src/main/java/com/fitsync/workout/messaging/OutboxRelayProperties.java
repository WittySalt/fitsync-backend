package com.fitsync.workout.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fitsync.outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        Duration interval,
        int batchSize) {
}
