package com.fitsync.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fitsync.backend.messaging.SpringKafkaEventProducer;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class SpringKafkaEventProducerTests {

    @Test
    void sendsOutboxEventWithUserPartitionKeyAndHeaders() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        SpringKafkaEventProducer producer = new SpringKafkaEventProducer(kafkaTemplate);
        OutboxEventEntity event = buildEvent();

        producer.send(event);

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("user-events");
        assertThat(record.key()).isEqualTo(event.getUserId().toString());
        assertThat(record.value()).isEqualTo(event.getPayload());
        assertThat(new String(record.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("UserRegistered");
        assertThat(new String(record.headers().lastHeader("aggregate-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("AppUser");
    }

    private OutboxEventEntity buildEvent() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setTopic("user-events");
        event.setEventType("UserRegistered");
        event.setAggregateType("AppUser");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setUserId(UUID.randomUUID());
        event.setVersion(1);
        event.setPayload("{\"eventType\":\"UserRegistered\"}");
        event.setOccurredAt(Instant.parse("2026-06-11T14:00:00Z"));
        event.setCreatedAt(Instant.parse("2026-06-11T14:00:00Z"));
        return event;
    }
}
