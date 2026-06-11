package com.fitsync.workout.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxRelayProperties properties;

    public OutboxRelayScheduler(OutboxEventRelay outboxEventRelay,
                                OutboxRelayProperties properties) {
        this.outboxEventRelay = outboxEventRelay;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fitsync.outbox.relay.interval:PT30S}")
    public void publishPendingBatch() {
        outboxEventRelay.publishPendingBatch(properties.batchSize());
    }
}
