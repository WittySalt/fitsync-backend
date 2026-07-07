package com.fitsync.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "fitsync.kafka.listeners.streak-milestone-reached", name = "enabled", havingValue = "true")
public class StreakMilestoneReachedKafkaListener
        extends AbstractDomainEventKafkaListener<StreakMilestoneReachedPayload> {

    public StreakMilestoneReachedKafkaListener(ObjectMapper objectMapper,
                                               DomainEventConsumer<StreakMilestoneReachedPayload> consumer) {
        super(objectMapper, StreakMilestoneReachedPayload.class, "StreakMilestoneReached", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.analytics-events:analytics-events}",
            groupId = "${fitsync.kafka.consumer-groups.recommendation-service:recommendation-consumer-group}"
                    + "-streak-milestone-reached",
            autoStartup = "${fitsync.kafka.listeners.streak-milestone-reached.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
