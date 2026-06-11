package com.fitsync.backend.messaging;

import com.fitsync.backend.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
