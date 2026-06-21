package com.fitsync.contracts.events;

public interface DomainEventConsumer<T> {

    void consume(FitSyncDomainEvent<T> event);
}
