package com.fitsync.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.meal-logged", name = "enabled", havingValue = "true")
public class MealLoggedKafkaListener extends AbstractDomainEventKafkaListener<MealLoggedPayload> {

    public MealLoggedKafkaListener(ObjectMapper objectMapper,
                                   DomainEventConsumer<MealLoggedPayload> consumer) {
        super(objectMapper, MealLoggedPayload.class, "MealLogged", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.recommendation-service:recommendation-consumer-group}"
                    + "-meal-logged",
            autoStartup = "${fitsync.kafka.listeners.meal-logged.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
