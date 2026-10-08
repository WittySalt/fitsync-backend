package com.fitsync.contracts.events.workout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emitted by workout-service whenever the user's active plan, or which plan is active, changes:
 * activation, edit, deactivation, archive or deletion. It carries the whole weekly schedule of the
 * plan that is active now, so a consumer replaces what it holds; {@code planId} is null and
 * {@code days} empty when no plan is active. The user is carried on the {@code FitSyncDomainEvent}
 * envelope.
 */
public record WorkoutPlanScheduleChangedPayload(
        UUID planId,
        String planName,
        List<PlannedDay> days,
        Instant changedAt) {

    public WorkoutPlanScheduleChangedPayload {
        days = days == null ? List.of() : List.copyOf(days);
    }

    /**
     * One day of the weekly template. {@code dayOfWeek} is ISO (1 = Monday … 7 = Sunday);
     * {@code kind} is the plan day kind (e.g. STRENGTH, CARDIO, SPORT, MOBILITY, REST).
     */
    public record PlannedDay(
            int dayOfWeek,
            String kind,
            String title,
            Integer estimatedMinutes,
            int exerciseCount) {
    }
}
