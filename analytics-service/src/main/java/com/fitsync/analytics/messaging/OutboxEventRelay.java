package com.fitsync.analytics.messaging;

/** Sends pending outbox events to Kafka. */
public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
