package com.fitsync.web.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.contracts.events.FitSyncDomainEvent;

/**
 * Builds the reader for a {@code FitSyncDomainEvent<T>} envelope. Unknown properties are ignored so a
 * producer can add a payload field before every consumer knows about it.
 */
public final class DomainEventObjectReaders {

    private DomainEventObjectReaders() {
    }

    public static <T> ObjectReader readerFor(ObjectMapper objectMapper, Class<T> payloadType) {
        JavaType eventType = objectMapper.getTypeFactory()
                .constructParametricType(FitSyncDomainEvent.class, payloadType);
        return objectMapper.readerFor(eventType)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
