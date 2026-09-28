package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the bounded "history before a date" projection: the heaviest logged set weight for
 * a single exercise, grouped server-side rather than loading full session/log entities. */
public record ExerciseTopWeightRow(UUID exerciseId, BigDecimal maxWeightKg) {
}
