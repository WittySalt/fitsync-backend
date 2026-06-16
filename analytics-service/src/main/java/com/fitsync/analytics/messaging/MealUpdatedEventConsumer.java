package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;

public interface MealUpdatedEventConsumer {

    void consume(FitSyncDomainEvent<MealUpdatedPayload> event);
}
