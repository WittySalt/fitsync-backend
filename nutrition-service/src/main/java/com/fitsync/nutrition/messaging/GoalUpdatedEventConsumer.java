package com.fitsync.nutrition.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;

public interface GoalUpdatedEventConsumer {

    void consume(FitSyncDomainEvent<GoalUpdatedPayload> event);
}
