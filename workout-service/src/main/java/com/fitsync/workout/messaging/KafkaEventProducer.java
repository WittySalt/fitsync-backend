package com.fitsync.workout.messaging;

import com.fitsync.workout.messaging.entity.OutboxEventEntity;

public interface KafkaEventProducer {

    void send(OutboxEventEntity event);
}
