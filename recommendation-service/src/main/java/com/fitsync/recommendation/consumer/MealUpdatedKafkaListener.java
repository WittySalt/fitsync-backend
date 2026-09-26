package com.fitsync.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.meal-updated", name = "enabled", havingValue = "true")
public class MealUpdatedKafkaListener extends AbstractDomainEventKafkaListener<MealUpdatedPayload> {

    public MealUpdatedKafkaListener(ObjectMapper objectMapper,
                                    DomainEventConsumer<MealUpdatedPayload> consumer) {
        super(objectMapper, MealUpdatedPayload.class, "MealUpdated", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.recommendation-service:recommendation-consumer-group}"
                    + "-meal-updated",
            autoStartup = "${fitsync.kafka.listeners.meal-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
