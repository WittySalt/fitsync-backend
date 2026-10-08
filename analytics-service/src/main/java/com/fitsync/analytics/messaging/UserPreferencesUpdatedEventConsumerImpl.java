package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import org.springframework.stereotype.Service;

/** Handles UserPreferencesUpdated by storing the user's timezone on the dashboard snapshot. */
@Service
public class UserPreferencesUpdatedEventConsumerImpl implements DomainEventConsumer<UserPreferencesUpdatedPayload> {

    private final UserTimezoneProjectionService timezoneProjectionService;

    public UserPreferencesUpdatedEventConsumerImpl(UserTimezoneProjectionService timezoneProjectionService) {
        this.timezoneProjectionService = timezoneProjectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<UserPreferencesUpdatedPayload> event) {
        UserPreferencesUpdatedPayload payload = event.payload();
        timezoneProjectionService.projectTimezone(event, payload == null ? null : payload.timezone());
    }
}
