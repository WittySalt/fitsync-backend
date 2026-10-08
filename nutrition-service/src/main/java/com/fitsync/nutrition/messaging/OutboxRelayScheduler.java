package com.fitsync.nutrition.messaging;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/** Drains the outbox on a fixed delay when {@code fitsync.outbox.relay.enabled} is on. */
@Component
@ConditionalOnProperty(prefix = "fitsync.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxRelayProperties outboxRelayProperties;

    public OutboxRelayScheduler(OutboxEventRelay outboxEventRelay,
                                OutboxRelayProperties outboxRelayProperties) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxRelayProperties = outboxRelayProperties;
    }

    /**
     * Registers the relay with the delay from {@link OutboxRelayProperties}, which has already been checked,
     * rather than the raw property, so a zero or negative delay cannot make the relay spin.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::publishPendingBatch, Duration.ofMillis(outboxRelayProperties.fixedDelayMs()));
    }

    public void publishPendingBatch() {
        int publishedCount = outboxEventRelay.publishPendingBatch(outboxRelayProperties.batchSize());
        if (publishedCount > 0) {
            log.info("Published {} nutrition-service outbox events", publishedCount);
        }
    }
}
