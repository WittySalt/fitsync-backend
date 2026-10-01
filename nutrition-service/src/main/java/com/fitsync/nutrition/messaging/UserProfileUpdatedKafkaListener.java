package com.fitsync.nutrition.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-profile-updated", name = "enabled", havingValue = "true")
public class UserProfileUpdatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(UserProfileUpdatedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<UserProfileUpdatedPayload> consumer;

    public UserProfileUpdatedKafkaListener(ObjectMapper objectMapper,
                                              DomainEventConsumer<UserProfileUpdatedPayload> consumer) {
        this.consumer = consumer;
        this.eventReader = DomainEventObjectReaders.readerFor(objectMapper, UserProfileUpdatedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.nutrition-service:nutrition-service-consumer-group}-user-profile-updated",
            autoStartup = "${fitsync.kafka.listeners.user-profile-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = deserialize(payload);
        if (!"UserProfileUpdated".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<UserProfileUpdatedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize UserProfileUpdated event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
