package com.fitsync.notification.handler;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletionRequestedPayload;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.service.EmailComposer;
import com.fitsync.notification.service.EmailQueue;
import com.fitsync.notification.service.RecipientStore;
import com.fitsync.notification.service.RecipientTime;

/**
 * Marks the account as pending deletion, which stops every other email, and queues E6 at once.
 * The request time is the dedupe key, so a replayed event does not send the notice twice while a
 * second request after a cancellation does.
 */
@Service
public class AccountDeletionRequestedHandler implements DomainEventConsumer<AccountDeletionRequestedPayload> {

    private final RecipientStore recipients;
    private final EmailQueue queue;
    private final EmailComposer composer;
    private final Clock clock;

    public AccountDeletionRequestedHandler(RecipientStore recipients, EmailQueue queue, EmailComposer composer,
                                           Clock clock) {
        this.recipients = recipients;
        this.queue = queue;
        this.composer = composer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<AccountDeletionRequestedPayload> event) {
        if (event.userId() == null || event.payload() == null) {
            return;
        }
        AccountDeletionRequestedPayload payload = event.payload();
        Instant now = clock.instant();
        Instant requestedAt = firstNonNull(payload.requestedAt(), event.occurredAt(), now);

        RecipientEntity recipient = recipients.getOrCreate(event.userId());
        // The column doubles as the "pending" mark, so it is set even if the date is missing.
        recipient.setDeletionScheduledFor(firstNonNull(payload.scheduledFor(), requestedAt, now));
        recipients.save(recipient);

        queue.queue(event.userId(), EmailKind.DELETION_SCHEDULED, requestedAt.toString(),
                composer.deletionScheduled(recipient, payload.scheduledFor(), RecipientTime.zoneOf(recipient)),
                now, payload.scheduledFor());
    }

    private static Instant firstNonNull(Instant first, Instant second, Instant third) {
        if (first != null) {
            return first;
        }
        return second != null ? second : third;
    }
}
