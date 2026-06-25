package com.fitsync.web.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared base for the per-service Kafka listeners that adapt a single domain-event
 * type off a shared topic. Subclasses only declare the {@code @KafkaListener} binding
 * (topic / group / autoStartup) and delegate the message body to {@link #handle},
 * so the deserialize / event-type filter / log-sanitisation boilerplate lives here once
 * instead of being copied into every consumer.
 *
 * @param <T> the event payload type
 */
public abstract class AbstractDomainEventKafkaListener<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractDomainEventKafkaListener.class);

    private final ObjectReader eventReader;
    private final String expectedEventType;
    private final DomainEventConsumer<T> consumer;

    protected AbstractDomainEventKafkaListener(ObjectMapper objectMapper,
                                               Class<T> payloadType,
                                               String expectedEventType,
                                               DomainEventConsumer<T> consumer) {
        this.consumer = consumer;
        this.expectedEventType = expectedEventType;
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, payloadType);
        this.eventReader = objectMapper.readerFor(eventType);
    }

    /**
     * Deserialize the raw payload, skip events whose type does not match this listener,
     * and dispatch matching events to the consumer.
     */
    protected void handle(String payload, String topic, String key) {
        FitSyncDomainEvent<T> event = deserialize(payload);
        if (!expectedEventType.equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<T> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize " + expectedEventType + " event", ex);
        }
    }

    private static String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
