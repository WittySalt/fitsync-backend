package com.fitsync.workout.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import com.fitsync.workout.messaging.entity.OutboxEventEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka", name = "enabled", havingValue = "true")
public class SpringKafkaEventProducer implements KafkaEventProducer {

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    public SpringKafkaEventProducer(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    @Override
    public void send(OutboxEventEntity event) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            throw new IllegalStateException("Missing required bean: KafkaTemplate");
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                partitionKey(event),
                event.getPayload());
        record.headers().add(new RecordHeader("event-id", event.getEventId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-type", event.getEventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-type", event.getAggregateType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-id", event.getAggregateId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-version",
                Integer.toString(event.getEventVersion()).getBytes(StandardCharsets.UTF_8)));
        if (event.getUserId() != null) {
            record.headers().add(new RecordHeader("user-id", event.getUserId().toString().getBytes(StandardCharsets.UTF_8)));
        }

        try {
            kafkaTemplate.send(record).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Kafka event", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to publish Kafka event", ex.getCause());
        }
    }

    private String partitionKey(OutboxEventEntity event) {
        if (event.getUserId() != null) {
            return event.getUserId().toString();
        }
        return event.getAggregateId();
    }
}
