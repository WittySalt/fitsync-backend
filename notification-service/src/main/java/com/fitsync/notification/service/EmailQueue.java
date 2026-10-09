package com.fitsync.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.service.EmailComposer.ComposedEmail;

/**
 * Records an email as decided. The record is what keeps an email from going out twice: a second
 * decision with the same user, kind and dedupe key is ignored, whether the first one was sent,
 * dropped or is still waiting.
 *
 * <p>An email that is already due is announced with {@link DueEmailQueued}, so it can go out as soon as
 * the caller's transaction commits instead of on the next tick.
 */
@Service
public class EmailQueue {

    private static final Logger log = LoggerFactory.getLogger(EmailQueue.class);

    private static final Duration ADMIN_VALID_FOR = Duration.ofHours(1);

    private final EmailRepository emails;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public EmailQueue(EmailRepository emails, Clock clock, ApplicationEventPublisher events) {
        this.emails = emails;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Queues an email unless one with the same key exists. Must run inside the caller's transaction.
     *
     * @return true when a new email was queued
     */
    public boolean queue(UUID userId, EmailKind kind, String dedupeKey, ComposedEmail content,
                         Instant dueAt, Instant expiresAt) {
        if (emails.existsByUserIdAndKindAndDedupeKey(userId, kind, dedupeKey)) {
            return false;
        }
        Instant now = clock.instant();
        EmailEntity email = new EmailEntity(userId, kind, dedupeKey, content.subject(), content.bodyText(),
                dueAt, expiresAt, now);
        emails.save(email);
        log.info("event=email_queued emailId={} kind={}", email.getEmailId(), kind);
        if (!dueAt.isAfter(now)) {
            events.publishEvent(new DueEmailQueued(email.getEmailId()));
        }
        return true;
    }

    /**
     * Queues an email an admin asked for, due now. Its dedupe key is its own, so it neither blocks
     * nor is blocked by the user's ordinary email of the same kind. It lasts an hour, which covers
     * the retries, so an email that SES keeps refusing does not arrive much later.
     *
     * @return the id of the new email
     */
    public UUID queueForAdmin(UUID userId, EmailKind kind, ComposedEmail content, UUID adminId) {
        Instant now = clock.instant();
        EmailEntity email = new EmailEntity(userId, kind, "admin:" + UUID.randomUUID(), content.subject(),
                content.bodyText(), now, now.plus(ADMIN_VALID_FOR), now);
        email.markAdminSent(adminId);
        emails.save(email);
        log.info("event=email_queued emailId={} kind={} trigger=ADMIN", email.getEmailId(), kind);
        events.publishEvent(new DueEmailQueued(email.getEmailId()));
        return email.getEmailId();
    }
}
