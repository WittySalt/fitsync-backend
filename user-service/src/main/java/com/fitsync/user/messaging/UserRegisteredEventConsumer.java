package com.fitsync.user.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;

public interface UserRegisteredEventConsumer {

    void consume(FitSyncDomainEvent<UserRegisteredPayload> event);
}
