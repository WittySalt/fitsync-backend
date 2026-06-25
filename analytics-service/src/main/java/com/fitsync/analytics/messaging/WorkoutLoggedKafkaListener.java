package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.workout-logged", name = "enabled", havingValue = "true")
public class WorkoutLoggedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(WorkoutLoggedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<WorkoutLoggedPayload> consumer;

    public WorkoutLoggedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<WorkoutLoggedPayload> consumer) {
        this.consumer = consumer;
        this.eventReader = DomainEventObjectReaders.readerFor(objectMapper, WorkoutLoggedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.workout-events:workout-events}",
            groupId = "${fitsync.kafka.consumer-groups.analytics-service:analytics-consumer-group}-workout-logged",
            autoStartup = "${fitsync.kafka.listeners.workout-logged.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<WorkoutLoggedPayload> event = deserialize(payload);
        if (!"WorkoutLogged".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<WorkoutLoggedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize WorkoutLogged event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
