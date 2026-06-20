package com.fitsync.contracts.events.analytics;

import java.time.LocalDate;

/**
 * Emitted when a user's active workout streak first reaches a configured milestone
 * (e.g. 7, 30, 100 days). The affected user is carried on the {@code FitSyncDomainEvent} envelope.
 */
public record StreakMilestoneReachedPayload(
        int streakDays,
        int milestone,
        LocalDate achievedDate) {
}
