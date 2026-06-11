package com.fitsync.backend.messaging;

import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FallbackKafkaEventProducer implements KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(FallbackKafkaEventProducer.class);

    @Override
    public void send(OutboxEventEntity event) {
        log.info("Kafka publish fallback topic={} eventType={} eventId={}",
                sanitizeForLog(event.getTopic()),
                sanitizeForLog(event.getEventType()),
                sanitizeForLog(event.getEventId()));
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
