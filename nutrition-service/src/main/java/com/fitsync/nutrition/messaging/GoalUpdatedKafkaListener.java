package com.fitsync.nutrition.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.goal-updated", name = "enabled", havingValue = "true")
public class GoalUpdatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(GoalUpdatedKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final GoalUpdatedEventConsumer goalUpdatedEventConsumer;
    private final JavaType eventType;

    public GoalUpdatedKafkaListener(ObjectMapper objectMapper,
                                    GoalUpdatedEventConsumer goalUpdatedEventConsumer) {
        this.objectMapper = objectMapper;
        this.goalUpdatedEventConsumer = goalUpdatedEventConsumer;
        this.eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, GoalUpdatedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.progress-events:progress-events}",
            groupId = "${fitsync.kafka.consumer-groups.nutrition-service:nutrition-service-consumer-group}",
            autoStartup = "${fitsync.kafka.listeners.goal-updated.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<GoalUpdatedPayload> event = deserialize(payload);
        if (!"GoalUpdated".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}", event.eventType(), topic, key);
            return;
        }
        goalUpdatedEventConsumer.consume(event);
    }

    private FitSyncDomainEvent<GoalUpdatedPayload> deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize GoalUpdated event", ex);
        }
    }
}
