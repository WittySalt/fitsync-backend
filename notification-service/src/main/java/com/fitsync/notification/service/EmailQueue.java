package com.fitsync.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.service.EmailComposer.ComposedEmail;

/**
 * Records an email as decided. The record is what keeps an email from going out twice: a second
 * decision with the same user, kind and dedupe key is ignored, whether the first one was sent,
 * dropped or is still waiting.
 */
@Service
public class EmailQueue {

    private static final Logger log = LoggerFactory.getLogger(EmailQueue.class);

    private final EmailRepository emails;
    private final Clock clock;

    public EmailQueue(EmailRepository emails, Clock clock) {
        this.emails = emails;
        this.clock = clock;
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
        EmailEntity email = new EmailEntity(userId, kind, dedupeKey, content.subject(), content.bodyText(),
                dueAt, expiresAt, clock.instant());
        emails.save(email);
        log.info("event=email_queued emailId={} kind={}", email.getEmailId(), kind);
        return true;
    }
}
