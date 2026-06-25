package com.fitsync.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.FixedBackOff;

class CommonKafkaListenerErrorConfigurationTests {

    @Test
    void marksInvalidPayloadExceptionsAsNotRetryable() {
        InspectableErrorHandler errorHandler = new InspectableErrorHandler();

        CommonKafkaListenerErrorConfiguration.configureNotRetryableExceptions(errorHandler);

        assertThat(errorHandler.isRetryable(new IllegalArgumentException("bad payload"))).isFalse();
        assertThat(errorHandler.isRetryable(new ListenerExecutionFailedException(
                "listener failed",
                new IllegalArgumentException("bad payload")))).isFalse();
        assertThat(errorHandler.isRetryable(new IllegalStateException("transient failure"))).isTrue();
    }

    @Test
    void extractsEventTypeFromJsonPayloadForFailureLogs() {
        String payload = """
                {"eventType":"GoalUpdated","payload":{"targetCalories":2200}}
                """;

        String eventType = CommonKafkaListenerErrorConfiguration.extractEventType(payload, new ObjectMapper());

        assertThat(eventType).isEqualTo("GoalUpdated");
    }

    @Test
    void ignoresMalformedPayloadWhenExtractingEventType() {
        String eventType = CommonKafkaListenerErrorConfiguration.extractEventType("not-json", new ObjectMapper());

        assertThat(eventType).isNull();
    }

    @Test
    void publishesFailedRecordToDeadLetterTopicWhenKafkaOperationsIsAvailable() {
        KafkaOperations<Object, Object> kafkaOperations = mockKafkaOperations();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "fitness.events",
                2,
                42L,
                "user-1",
                "{\"eventType\":\"GoalUpdated\"}");
        ConsumerRecordRecoverer recoverer = CommonKafkaListenerErrorConfiguration.buildRecordRecoverer(
                new ObjectMapper(),
                kafkaOperations);

        recoverer.accept(record, new RuntimeException("listener failed"));

        ProducerRecord<Object, Object> deadLetterRecord = captureDeadLetterRecord(kafkaOperations);
        assertThat(deadLetterRecord.topic()).isEqualTo("fitness.events.DLT");
        assertThat(deadLetterRecord.partition()).isNull();
        assertThat(deadLetterRecord.key()).isEqualTo("user-1");
        assertThat(deadLetterRecord.value()).isEqualTo("{\"eventType\":\"GoalUpdated\"}");
    }

    @Test
    void publishesNotRetryableFailedRecordToDeadLetterTopic() {
        KafkaOperations<Object, Object> kafkaOperations = mockKafkaOperations();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "fitness.events",
                0,
                7L,
                "user-2",
                "{\"eventType\":\"Malformed\"}");
        ConsumerRecordRecoverer recoverer = CommonKafkaListenerErrorConfiguration.buildRecordRecoverer(
                new ObjectMapper(),
                kafkaOperations);

        recoverer.accept(record, new IllegalArgumentException("bad payload"));

        ProducerRecord<Object, Object> deadLetterRecord = captureDeadLetterRecord(kafkaOperations);
        assertThat(deadLetterRecord.topic()).isEqualTo("fitness.events.DLT");
        assertThat(deadLetterRecord.partition()).isNull();
    }

    @Test
    void fallsBackToLogOnlyRecovererWhenKafkaOperationsIsMissing() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "fitness.events",
                0,
                7L,
                "user-3",
                "{\"eventType\":\"GoalUpdated\"}");
        ConsumerRecordRecoverer recoverer = CommonKafkaListenerErrorConfiguration.buildRecordRecoverer(
                new ObjectMapper(),
                null);

        assertThatNoException().isThrownBy(() -> recoverer.accept(record, new RuntimeException("listener failed")));
    }

    @SuppressWarnings("unchecked")
    private static KafkaOperations<Object, Object> mockKafkaOperations() {
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);
        when(kafkaOperations.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(
                mock(SendResult.class)));
        return kafkaOperations;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ProducerRecord<Object, Object> captureDeadLetterRecord(KafkaOperations<Object, Object> kafkaOperations) {
        ArgumentCaptor<ProducerRecord<Object, Object>> captor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(kafkaOperations).send(captor.capture());
        return captor.getValue();
    }

    private static final class InspectableErrorHandler extends DefaultErrorHandler {

        private InspectableErrorHandler() {
            super(new FixedBackOff(0L, 0L));
        }

        private boolean isRetryable(Exception exception) {
            return getClassifier().classify(exception);
        }
    }
}
