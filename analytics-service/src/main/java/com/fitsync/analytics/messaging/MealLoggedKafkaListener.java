package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.meal-logged", name = "enabled", havingValue = "true")
public class MealLoggedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(MealLoggedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<MealLoggedPayload> consumer;

    public MealLoggedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<MealLoggedPayload> consumer) {
        this.consumer = consumer;
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, MealLoggedPayload.class);
        this.eventReader = objectMapper.readerFor(eventType);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-meal-logged",
            autoStartup = "${fitsync.kafka.listeners.meal-logged.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<MealLoggedPayload> event = deserialize(payload);
        if (!"MealLogged".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<MealLoggedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize MealLogged event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
