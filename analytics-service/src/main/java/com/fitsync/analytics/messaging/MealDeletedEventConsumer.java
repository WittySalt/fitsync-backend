package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;

public interface MealDeletedEventConsumer {

    void consume(FitSyncDomainEvent<MealDeletedPayload> event);
}
