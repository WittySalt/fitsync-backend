package com.fitsync.nutrition.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-preferences-updated", name = "enabled", havingValue = "true")
public class UserPreferencesUpdatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesUpdatedKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final UserPreferencesUpdatedEventConsumer consumer;
    private final JavaType eventType;

    public UserPreferencesUpdatedKafkaListener(ObjectMapper objectMapper,
                                              UserPreferencesUpdatedEventConsumer consumer) {
        this.objectMapper = objectMapper;
        this.consumer = consumer;
        this.eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, UserPreferencesUpdatedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.nutrition-service:nutrition-service-consumer-group}",
            autoStartup = "${fitsync.kafka.listeners.user-preferences-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<UserPreferencesUpdatedPayload> event = deserialize(payload);
        if (!"UserPreferencesUpdated".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}", event.eventType(), topic, key);
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<UserPreferencesUpdatedPayload> deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize UserPreferencesUpdated event", ex);
        }
    }
}
