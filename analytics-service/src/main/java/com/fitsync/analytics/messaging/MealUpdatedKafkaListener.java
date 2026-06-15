package com.fitsync.analytics.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.meal-updated", name = "enabled", havingValue = "true")
public class MealUpdatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(MealUpdatedKafkaListener.class);

    private final ObjectReader eventReader;
    private final MealUpdatedEventConsumer consumer;

    public MealUpdatedKafkaListener(ObjectMapper objectMapper, MealUpdatedEventConsumer consumer) {
        this.consumer = consumer;
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, MealUpdatedPayload.class);
        this.eventReader = objectMapper.readerFor(eventType);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-meal-updated",
            autoStartup = "${fitsync.kafka.listeners.meal-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<MealUpdatedPayload> event = deserialize(payload);
        if (!"MealUpdated".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<MealUpdatedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize MealUpdated event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
