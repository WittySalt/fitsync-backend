package com.fitsync.user.messaging;

import com.fitsync.user.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
