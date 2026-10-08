package com.fitsync.user.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.UserRegisteredPayload;

public interface UserRegisteredEventConsumer extends DomainEventConsumer<UserRegisteredPayload> {
}
