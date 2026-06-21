package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.workout-deleted", name = "enabled", havingValue = "true")
public class WorkoutDeletedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(WorkoutDeletedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<WorkoutDeletedPayload> consumer;

    public WorkoutDeletedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<WorkoutDeletedPayload> consumer) {
        this.consumer = consumer;
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, WorkoutDeletedPayload.class);
        this.eventReader = objectMapper.readerFor(eventType);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.workout-events:workout-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-workout-deleted",
            autoStartup = "${fitsync.kafka.listeners.workout-deleted.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<WorkoutDeletedPayload> event = deserialize(payload);
        if (!"WorkoutDeleted".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<WorkoutDeletedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize WorkoutDeleted event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
