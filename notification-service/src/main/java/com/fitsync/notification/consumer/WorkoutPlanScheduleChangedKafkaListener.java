package com.fitsync.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.workout-plan-schedule-changed", name = "enabled", havingValue = "true")
public class WorkoutPlanScheduleChangedKafkaListener extends AbstractDomainEventKafkaListener<WorkoutPlanScheduleChangedPayload> {

    public WorkoutPlanScheduleChangedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<WorkoutPlanScheduleChangedPayload> consumer) {
        super(objectMapper, WorkoutPlanScheduleChangedPayload.class, "WorkoutPlanScheduleChanged", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.workout-events:workout-events}",
            groupId = "${fitsync.kafka.consumer-groups.notification-service:notification-consumer-group}"
                    + "-workout-plan-schedule-changed",
            autoStartup = "${fitsync.kafka.listeners.workout-plan-schedule-changed.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
