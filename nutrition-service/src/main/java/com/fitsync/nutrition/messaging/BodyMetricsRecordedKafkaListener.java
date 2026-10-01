package com.fitsync.nutrition.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.web.messaging.DomainEventObjectReaders;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.body-metrics-recorded", name = "enabled", havingValue = "true")
public class BodyMetricsRecordedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(BodyMetricsRecordedKafkaListener.class);

    private final ObjectReader eventReader;
    private final DomainEventConsumer<BodyMetricsRecordedPayload> consumer;

    public BodyMetricsRecordedKafkaListener(ObjectMapper objectMapper,
                                              DomainEventConsumer<BodyMetricsRecordedPayload> consumer) {
        this.consumer = consumer;
        this.eventReader = DomainEventObjectReaders.readerFor(objectMapper, BodyMetricsRecordedPayload.class);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.progress-events:progress-events}",
            groupId = "${fitsync.kafka.consumer-groups.nutrition-service:nutrition-service-consumer-group}-body-metrics-recorded",
            autoStartup = "${fitsync.kafka.listeners.body-metrics-recorded.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = deserialize(payload);
        if (!"BodyMetricsRecorded".equals(event.eventType())) {
            log.debug("Ignoring eventType={} from topic={} key={}",
                    sanitizeForLog(event.eventType()), sanitizeForLog(topic), sanitizeForLog(key));
            return;
        }
        consumer.consume(event);
    }

    private FitSyncDomainEvent<BodyMetricsRecordedPayload> deserialize(String payload) {
        try {
            return eventReader.readValue(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize BodyMetricsRecorded event", ex);
        }
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
