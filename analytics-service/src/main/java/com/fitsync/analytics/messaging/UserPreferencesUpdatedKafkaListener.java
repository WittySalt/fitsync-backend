package com.fitsync.analytics.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-preferences-updated", name = "enabled", havingValue = "true")
public class UserPreferencesUpdatedKafkaListener extends AbstractDomainEventKafkaListener<UserPreferencesUpdatedPayload> {

    public UserPreferencesUpdatedKafkaListener(ObjectMapper objectMapper,
                                               DomainEventConsumer<UserPreferencesUpdatedPayload> consumer) {
        super(objectMapper, UserPreferencesUpdatedPayload.class, "UserPreferencesUpdated", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-user-preferences-updated",
            autoStartup = "${fitsync.kafka.listeners.user-preferences-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
