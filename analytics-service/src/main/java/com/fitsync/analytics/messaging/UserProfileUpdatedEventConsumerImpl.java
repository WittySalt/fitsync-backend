package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import org.springframework.stereotype.Service;

/** Handles UserProfileUpdated by storing the user's timezone on the dashboard snapshot. */
@Service
public class UserProfileUpdatedEventConsumerImpl implements DomainEventConsumer<UserProfileUpdatedPayload> {

    private final UserTimezoneProjectionService timezoneProjectionService;

    public UserProfileUpdatedEventConsumerImpl(UserTimezoneProjectionService timezoneProjectionService) {
        this.timezoneProjectionService = timezoneProjectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<UserProfileUpdatedPayload> event) {
        UserProfileUpdatedPayload payload = event.payload();
        timezoneProjectionService.projectTimezone(event, payload == null ? null : payload.timezone());
    }
}
