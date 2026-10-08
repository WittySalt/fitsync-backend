package com.fitsync.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.body-metrics-recorded", name = "enabled", havingValue = "true")
public class BodyMetricsRecordedKafkaListener extends AbstractDomainEventKafkaListener<BodyMetricsRecordedPayload> {

    public BodyMetricsRecordedKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<BodyMetricsRecordedPayload> consumer) {
        super(objectMapper, BodyMetricsRecordedPayload.class, "BodyMetricsRecorded", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.progress-events:progress-events}",
            groupId = "${fitsync.kafka.consumer-groups.notification-service:notification-consumer-group}"
                    + "-body-metrics-recorded",
            autoStartup = "${fitsync.kafka.listeners.body-metrics-recorded.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
