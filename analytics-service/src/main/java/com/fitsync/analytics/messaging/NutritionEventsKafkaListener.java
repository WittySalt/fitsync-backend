package com.fitsync.analytics.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * The one consumer of the nutrition topic. MealLogged, MealUpdated and MealDeleted share one consumer group,
 * so the events of a key arrive in the order they were published and a delete can no longer overtake the log
 * it removes (progress review fixes, section 2). Each type keeps its own flag: the listener runs when any of
 * the three is on and hands an event on only when its own flag is on.
 */
@Component
@ConditionalOnExpression("${fitsync.kafka.listeners.meal-logged.enabled:false}"
        + " or ${fitsync.kafka.listeners.meal-updated.enabled:false}"
        + " or ${fitsync.kafka.listeners.meal-deleted.enabled:false}")
public class NutritionEventsKafkaListener {

    static final String MEAL_LOGGED = "MealLogged";
    static final String MEAL_UPDATED = "MealUpdated";
    static final String MEAL_DELETED = "MealDeleted";
    private static final String LOGGED_FLAG = "${fitsync.kafka.listeners.meal-logged.enabled:false}";
    private static final String UPDATED_FLAG = "${fitsync.kafka.listeners.meal-updated.enabled:false}";
    private static final String DELETED_FLAG = "${fitsync.kafka.listeners.meal-deleted.enabled:false}";

    private static final Logger log = LoggerFactory.getLogger(NutritionEventsKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final ObjectReader loggedReader;
    private final ObjectReader updatedReader;
    private final ObjectReader deletedReader;
    private final DomainEventConsumer<MealLoggedPayload> loggedConsumer;
    private final DomainEventConsumer<MealUpdatedPayload> updatedConsumer;
    private final DomainEventConsumer<MealDeletedPayload> deletedConsumer;
    private final boolean loggedEnabled;
    private final boolean updatedEnabled;
    private final boolean deletedEnabled;

    public NutritionEventsKafkaListener(ObjectMapper objectMapper,
                                        DomainEventConsumer<MealLoggedPayload> loggedConsumer,
                                        DomainEventConsumer<MealUpdatedPayload> updatedConsumer,
                                        DomainEventConsumer<MealDeletedPayload> deletedConsumer,
                                        @Value(LOGGED_FLAG) boolean loggedEnabled,
                                        @Value(UPDATED_FLAG) boolean updatedEnabled,
                                        @Value(DELETED_FLAG) boolean deletedEnabled) {
        this.objectMapper = objectMapper;
        this.loggedReader = DomainEventObjectReaders.readerFor(objectMapper, MealLoggedPayload.class);
        this.updatedReader = DomainEventObjectReaders.readerFor(objectMapper, MealUpdatedPayload.class);
        this.deletedReader = DomainEventObjectReaders.readerFor(objectMapper, MealDeletedPayload.class);
        this.loggedConsumer = loggedConsumer;
        this.updatedConsumer = updatedConsumer;
        this.deletedConsumer = deletedConsumer;
        this.loggedEnabled = loggedEnabled;
        this.updatedEnabled = updatedEnabled;
        this.deletedEnabled = deletedEnabled;
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.nutrition-events:nutrition-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-nutrition",
            autoStartup = "#{${fitsync.kafka.listeners.meal-logged.enabled:false}"
                    + " or ${fitsync.kafka.listeners.meal-updated.enabled:false}"
                    + " or ${fitsync.kafka.listeners.meal-deleted.enabled:false}}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String eventType = eventType(payload);
        if (MEAL_LOGGED.equals(eventType) && loggedEnabled) {
            FitSyncDomainEvent<MealLoggedPayload> event = read(loggedReader, payload, eventType);
            loggedConsumer.consume(event);
        } else if (MEAL_UPDATED.equals(eventType) && updatedEnabled) {
            FitSyncDomainEvent<MealUpdatedPayload> event = read(updatedReader, payload, eventType);
            updatedConsumer.consume(event);
        } else if (MEAL_DELETED.equals(eventType) && deletedEnabled) {
            FitSyncDomainEvent<MealDeletedPayload> event = read(deletedReader, payload, eventType);
            deletedConsumer.consume(event);
        } else {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(eventType), sanitizeForLog(topic), sanitizeForLog(key));
        }
    }

    private String eventType(String payload) {
        try {
            JsonNode type = objectMapper.readTree(payload).path("eventType");
            return type.isTextual() ? type.asText() : null;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to read the event type of a nutrition event", ex);
        }
    }

    private static <T> FitSyncDomainEvent<T> read(ObjectReader reader, String payload, String eventType) {
        try {
            return reader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize " + eventType + " event", ex);
        }
    }

    private static String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
