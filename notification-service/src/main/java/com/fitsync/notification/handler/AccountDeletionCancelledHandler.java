package com.fitsync.notification.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletionCancelledPayload;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.service.RecipientStore;

/** The user signed in again before the removal date, so their emails start again as their settings say. */
@Service
public class AccountDeletionCancelledHandler implements DomainEventConsumer<AccountDeletionCancelledPayload> {

    private final RecipientRepository recipientRepository;
    private final RecipientStore recipients;

    public AccountDeletionCancelledHandler(RecipientRepository recipientRepository, RecipientStore recipients) {
        this.recipientRepository = recipientRepository;
        this.recipients = recipients;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<AccountDeletionCancelledPayload> event) {
        if (event.userId() == null) {
            return;
        }
        recipientRepository.findById(event.userId()).ifPresent(recipient -> {
            recipient.setDeletionScheduledFor(null);
            recipients.save(recipient);
        });
    }
}
