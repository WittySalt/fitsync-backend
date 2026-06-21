package com.fitsync.contracts.events;

public interface DomainEventPublisher {

    void publish(String topic, String aggregateType, String aggregateId, FitSyncDomainEvent<?> event);
}
