package com.fitsync.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
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

    private static final class InspectableErrorHandler extends DefaultErrorHandler {

        private InspectableErrorHandler() {
            super(new FixedBackOff(0L, 0L));
        }

        private boolean isRetryable(Exception exception) {
            return getClassifier().classify(exception);
        }
    }
}
