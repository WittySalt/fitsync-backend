package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the grouped projection that gives the heaviest logged set weight of each exercise
 * within each session, so records can be found without loading the sets themselves. */
public record SessionExerciseTopWeightRow(UUID sessionId, UUID exerciseId, BigDecimal maxWeightKg) {
}
