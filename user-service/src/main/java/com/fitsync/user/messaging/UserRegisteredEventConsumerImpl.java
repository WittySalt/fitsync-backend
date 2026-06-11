package com.fitsync.user.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.service.UserRegistrationProvisioningService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserRegisteredEventConsumerImpl implements UserRegisteredEventConsumer {

    private final UserRegistrationProvisioningService provisioningService;

    public UserRegisteredEventConsumerImpl(UserRegistrationProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserRegisteredPayload> event) {
        provisioningService.provisionFromRegistration(event);
    }
}
