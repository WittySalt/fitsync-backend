package com.fitsync.workout.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/** Drains the outbox on a fixed delay when {@code fitsync.outbox.relay.enabled} is on. */
@Component
@ConditionalOnProperty(prefix = "fitsync.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler implements SchedulingConfigurer {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxRelayProperties properties;

    public OutboxRelayScheduler(OutboxEventRelay outboxEventRelay,
                                OutboxRelayProperties properties) {
        this.outboxEventRelay = outboxEventRelay;
        this.properties = properties;
    }

    /**
     * Registers the relay with the delay from {@link OutboxRelayProperties}, which has already been checked,
     * rather than the raw property, so a zero or negative delay cannot make the relay spin.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::publishPendingBatch, properties.interval());
    }

    public void publishPendingBatch() {
        outboxEventRelay.publishPendingBatch(properties.batchSize());
    }
}
