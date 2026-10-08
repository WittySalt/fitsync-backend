package com.fitsync.notification.handler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.service.RecipientStore;

/** Keeps where to send and how to greet. Replaying the event writes the same values again. */
@Service
public class UserRegisteredHandler implements DomainEventConsumer<UserRegisteredPayload> {

    private static final int MAX_EMAIL = 320;
    private static final int MAX_NAME = 120;

    private final RecipientStore recipients;

    public UserRegisteredHandler(RecipientStore recipients) {
        this.recipients = recipients;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<UserRegisteredPayload> event) {
        if (event.userId() == null || event.payload() == null) {
            return;
        }
        RecipientEntity recipient = recipients.getOrCreate(event.userId());
        recipient.setEmail(Text.clip(event.payload().email(), MAX_EMAIL));
        recipient.setDisplayName(Text.clip(event.payload().displayName(), MAX_NAME));
        recipients.save(recipient);
    }
}
