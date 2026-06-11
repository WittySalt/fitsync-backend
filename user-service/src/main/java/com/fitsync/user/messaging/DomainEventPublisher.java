package com.fitsync.user.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;

public interface DomainEventPublisher {

    void publish(String topic, String aggregateType, String aggregateId, FitSyncDomainEvent<?> event);
}
