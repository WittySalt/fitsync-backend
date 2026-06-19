package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExerciseRecordSetRow(
        UUID sessionId,
        UUID exerciseLogId,
        UUID exerciseId,
        String exerciseNameSnapshot,
        Instant startedAt,
        Instant completedAt,
        Integer reps,
        BigDecimal weightKg) {
}
