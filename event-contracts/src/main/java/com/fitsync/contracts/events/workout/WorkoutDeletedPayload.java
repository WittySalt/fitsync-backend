package com.fitsync.contracts.events.workout;

import java.time.Instant;
import java.util.UUID;

public record WorkoutDeletedPayload(
        UUID sessionId,
        UUID planId,
        Instant deletedAt) {
}
