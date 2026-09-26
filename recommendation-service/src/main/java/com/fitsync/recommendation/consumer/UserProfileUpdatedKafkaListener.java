package com.fitsync.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.user-profile-updated", name = "enabled", havingValue = "true")
public class UserProfileUpdatedKafkaListener extends AbstractDomainEventKafkaListener<UserProfileUpdatedPayload> {

    public UserProfileUpdatedKafkaListener(ObjectMapper objectMapper,
                                           DomainEventConsumer<UserProfileUpdatedPayload> consumer) {
        super(objectMapper, UserProfileUpdatedPayload.class, "UserProfileUpdated", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.recommendation-service:recommendation-consumer-group}"
                    + "-user-profile-updated",
            autoStartup = "${fitsync.kafka.listeners.user-profile-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
