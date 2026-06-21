package com.fitsync.web.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnClass(DefaultErrorHandler.class)
public class CommonKafkaListenerErrorConfiguration {

    static final long KAFKA_LISTENER_RETRY_INTERVAL_MS = 2000L;
    static final long KAFKA_LISTENER_MAX_RETRY_ATTEMPTS = 3L;

    private static final Logger log = LoggerFactory.getLogger(CommonKafkaListenerErrorConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaListenerErrorHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return buildKafkaListenerErrorHandler(objectMapperProvider.getIfAvailable());
    }

    static DefaultErrorHandler buildKafkaListenerErrorHandler(ObjectMapper objectMapper) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> logFailedRecord(record, exception, objectMapper),
                new FixedBackOff(KAFKA_LISTENER_RETRY_INTERVAL_MS, KAFKA_LISTENER_MAX_RETRY_ATTEMPTS));
        configureNotRetryableExceptions(errorHandler);
        return errorHandler;
    }

    static void configureNotRetryableExceptions(DefaultErrorHandler errorHandler) {
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                DeserializationException.class,
                MessageConversionException.class,
                ConversionException.class,
                SerializationException.class,
                ClassCastException.class);
    }

    private static void logFailedRecord(ConsumerRecord<?, ?> record, Exception exception, ObjectMapper objectMapper) {
        Throwable rootCause = rootCause(exception);
        // Strip CR/LF inline (CRLF log injection, CWE-117). findsecbugs only recognizes the
        // replace() cleanser when it sits directly in the dataflow to the sink — a helper
        // method is treated as an unknown source, so the values are sanitized here, not via a call.
        String topic = String.valueOf(record.topic()).replace("\r", "_").replace("\n", "_");
        String key = String.valueOf(record.key()).replace("\r", "_").replace("\n", "_");
        String eventType = String.valueOf(extractEventType(record.value(), objectMapper))
                .replace("\r", "_").replace("\n", "_");
        String message = String.valueOf(rootCause.getMessage()).replace("\r", "_").replace("\n", "_");
        log.error(
                "event=kafka_listener_record_failed topic={} partition={} offset={} key={} eventType={} "
                        + "exceptionClass={} exceptionMessage={}",
                topic,
                record.partition(),
                record.offset(),
                key,
                eventType,
                rootCause.getClass().getName(),
                message,
                exception);
    }

    static String extractEventType(Object value, ObjectMapper objectMapper) {
        if (!(value instanceof String payload) || payload.isBlank() || objectMapper == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode eventType = node.get("eventType");
            if (eventType == null || eventType.isNull()) {
                return null;
            }
            return eventType.asText();
        } catch (JsonProcessingException | RuntimeException ignored) {
            return null;
        }
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
