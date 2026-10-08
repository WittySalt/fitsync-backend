package com.fitsync.user.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code user-events} and provisions the default profile and preference rows for each
 * {@code UserRegistered}; every other event type on the topic is skipped.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-registered", name = "enabled", havingValue = "true")
public class UserRegisteredKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredKafkaListener.class);

    private final ObjectReader eventReader;
    private final UserRegisteredEventConsumer userRegisteredEventConsumer;

    public UserRegisteredKafkaListener(ObjectMapper objectMapper,
                                       UserRegisteredEventConsumer userRegisteredEventConsumer) {
        this.userRegisteredEventConsumer = userRegisteredEventConsumer;
        this.eventReader = DomainEventObjectReaders.readerFor(objectMapper, UserRegisteredPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.user-service:user-service-consumer-group}",
            autoStartup = "${fitsync.kafka.listeners.user-registered.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<UserRegisteredPayload> event = deserialize(payload);
        if (!"UserRegistered".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()),
                    sanitizeForLog(topic),
                    sanitizeForLog(key));
            return;
        }
        userRegisteredEventConsumer.consume(event);
    }

    private FitSyncDomainEvent<UserRegisteredPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize UserRegistered event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
