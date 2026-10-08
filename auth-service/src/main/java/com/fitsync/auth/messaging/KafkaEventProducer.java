package com.fitsync.auth.messaging;

import com.fitsync.auth.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
