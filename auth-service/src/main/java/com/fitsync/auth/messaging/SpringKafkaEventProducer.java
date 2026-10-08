package com.fitsync.auth.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import com.fitsync.auth.messaging.entity.OutboxEventEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka", name = "enabled", havingValue = "true")
public class SpringKafkaEventProducer implements KafkaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public SpringKafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(OutboxEventEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                partitionKey(event),
                event.getPayload());
        record.headers().add(new RecordHeader("event-id", event.getEventId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-type", event.getAggregateType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-id", event.getAggregateId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-version",
                Integer.toString(event.getVersion()).getBytes(StandardCharsets.UTF_8)));
        if (event.getUserId() != null) {
            record.headers().add(new RecordHeader("user-id", event.getUserId().toString().getBytes(StandardCharsets.UTF_8)));
        }

        // Waits for the broker's acknowledgement so the relay only marks an event published once it is.
        try {
            kafkaTemplate.send(record).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Kafka event", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to publish Kafka event", ex.getCause());
        }
    }

    /** Keyed by user where there is one, so all of a user's events land on one partition and stay in order. */
    private String partitionKey(OutboxEventEntity event) {
        if (event.getUserId() != null) {
            return event.getUserId().toString();
        }
        return event.getAggregateId();
    }
}
