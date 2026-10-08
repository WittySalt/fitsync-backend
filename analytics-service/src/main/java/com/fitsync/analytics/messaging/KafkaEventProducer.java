package com.fitsync.analytics.messaging;

import com.fitsync.analytics.messaging.entity.OutboxEventEntity;

/** Sends one outbox row to Kafka; the relay treats a thrown exception as a failed send. */
public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
