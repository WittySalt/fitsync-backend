package com.fitsync.user.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code user-events} and provisions the default profile and preference rows for each
 * {@code UserRegistered}; every other event type on the topic is skipped. Deserialising, filtering and log
 * sanitising live in the shared base listener, as in the other services' listeners.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-registered", name = "enabled", havingValue = "true")
public class UserRegisteredKafkaListener extends AbstractDomainEventKafkaListener<UserRegisteredPayload> {

    public UserRegisteredKafkaListener(ObjectMapper objectMapper,
                                       UserRegisteredEventConsumer userRegisteredEventConsumer) {
        super(objectMapper, UserRegisteredPayload.class, "UserRegistered", userRegisteredEventConsumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.user-service:user-service-consumer-group}",
            autoStartup = "${fitsync.kafka.listeners.user-registered.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
