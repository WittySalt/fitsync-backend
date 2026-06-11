package com.fitsync.nutrition.messaging;

import com.fitsync.nutrition.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
