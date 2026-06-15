package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;

public interface MealLoggedEventConsumer {

    void consume(FitSyncDomainEvent<MealLoggedPayload> event);
}
