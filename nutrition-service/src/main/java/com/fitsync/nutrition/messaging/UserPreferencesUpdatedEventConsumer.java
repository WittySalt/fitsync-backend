package com.fitsync.nutrition.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;

public interface UserPreferencesUpdatedEventConsumer {

    void consume(FitSyncDomainEvent<UserPreferencesUpdatedPayload> event);
}
