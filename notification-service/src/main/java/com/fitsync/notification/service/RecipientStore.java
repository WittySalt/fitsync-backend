package com.fitsync.notification.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.repository.RecipientRepository;

/**
 * Finds or starts the recipient row. Events about one user arrive on different topics in no fixed
 * order, so whichever comes first creates the row and the others fill it in.
 */
@Service
public class RecipientStore {

    private final RecipientRepository recipients;
    private final Clock clock;

    public RecipientStore(RecipientRepository recipients, Clock clock) {
        this.recipients = recipients;
        this.clock = clock;
    }

    public RecipientEntity getOrCreate(UUID userId) {
        return recipients.findById(userId)
                .orElseGet(() -> new RecipientEntity(userId, clock.instant()));
    }

    public RecipientEntity save(RecipientEntity recipient) {
        recipient.setUpdatedAt(clock.instant());
        return recipients.save(recipient);
    }
}
