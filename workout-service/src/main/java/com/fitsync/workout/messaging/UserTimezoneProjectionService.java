package com.fitsync.workout.messaging;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.workout.entity.UserTimezoneEntity;
import com.fitsync.workout.repository.UserTimezoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the person's profile zone from {@code UserProfileUpdated} and {@code UserPreferencesUpdated}.
 * The projection is idempotent: an event older than the stored one is ignored, and replaying an event
 * leaves the same row, so no processed-event table is needed.
 */
@Service
public class UserTimezoneProjectionService {

    private final UserTimezoneRepository userTimezoneRepository;
    private final Clock clock;

    public UserTimezoneProjectionService(UserTimezoneRepository userTimezoneRepository, Clock clock) {
        this.userTimezoneRepository = userTimezoneRepository;
        this.clock = clock;
    }

    @Transactional
    public void projectTimezone(FitSyncDomainEvent<?> event, String timezone) {
        if (event == null || event.userId() == null) {
            return;
        }
        String normalized = normalize(timezone);
        if (normalized == null) {
            return;
        }
        Instant occurredAt = event.occurredAt() == null ? Instant.now(clock) : event.occurredAt();
        UserTimezoneEntity stored = userTimezoneRepository.findById(event.userId()).orElse(null);
        if (stored == null) {
            userTimezoneRepository.save(new UserTimezoneEntity(event.userId(), normalized, occurredAt));
        } else if (!occurredAt.isBefore(stored.getUpdatedAt())) {
            stored.change(normalized, occurredAt);
            userTimezoneRepository.save(stored);
        }
    }

    private static String normalize(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException ex) {
            return null;
        }
    }
}
