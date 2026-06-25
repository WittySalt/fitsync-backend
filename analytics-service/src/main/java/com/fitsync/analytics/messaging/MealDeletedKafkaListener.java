package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.meal-deleted", name = "enabled", havingValue = "true")
public class MealDeletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(MealDeletedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<MealDeletedPayload> consumer;

    public MealDeletedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<MealDeletedPayload> consumer) {
        this.consumer = consumer;
        this.eventReader = DomainEventObjectReaders.readerFor(objectMapper, MealDeletedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-meal-deleted",
            autoStartup = "${fitsync.kafka.listeners.meal-deleted.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<MealDeletedPayload> event = deserialize(payload);
        if (!"MealDeleted".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<MealDeletedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize MealDeleted event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
