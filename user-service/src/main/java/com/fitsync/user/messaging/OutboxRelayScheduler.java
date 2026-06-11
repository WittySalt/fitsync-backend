package com.fitsync.user.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.outbox.relay", name = "enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxRelayProperties outboxRelayProperties;

    public OutboxRelayScheduler(OutboxEventRelay outboxEventRelay,
                                OutboxRelayProperties outboxRelayProperties) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxRelayProperties = outboxRelayProperties;
    }

    @Scheduled(fixedDelayString = "${fitsync.outbox.relay.fixed-delay-ms:30000}")
    public void publishPendingBatch() {
        int publishedCount = outboxEventRelay.publishPendingBatch(outboxRelayProperties.batchSize());
        if (publishedCount > 0) {
            log.info("Published {} user-service outbox events", publishedCount);
        }
    }
}
