package com.fitsync.user.service;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;

public interface UserRegistrationProvisioningService {

    void provisionFromRegistration(FitSyncDomainEvent<UserRegisteredPayload> event);
}
