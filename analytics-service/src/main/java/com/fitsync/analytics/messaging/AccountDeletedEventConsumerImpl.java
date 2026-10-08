package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import org.springframework.stereotype.Component;

/** Handles AccountDeleted by purging every analytics read model row of the user. */
@Component
public class AccountDeletedEventConsumerImpl implements DomainEventConsumer<AccountDeletedPayload> {

    private final AccountDeletionProjectionService accountDeletionProjectionService;

    public AccountDeletedEventConsumerImpl(AccountDeletionProjectionService accountDeletionProjectionService) {
        this.accountDeletionProjectionService = accountDeletionProjectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<AccountDeletedPayload> event) {
        accountDeletionProjectionService.deleteUserData(event);
    }
}
