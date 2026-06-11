package com.fitsync.workout.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;

public interface DomainEventPublisher {

    void publish(String topic, String aggregateType, String aggregateId, FitSyncDomainEvent<?> event);
}
