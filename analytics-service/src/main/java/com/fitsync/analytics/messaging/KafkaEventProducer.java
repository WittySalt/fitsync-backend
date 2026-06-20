package com.fitsync.analytics.messaging;

import com.fitsync.analytics.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
