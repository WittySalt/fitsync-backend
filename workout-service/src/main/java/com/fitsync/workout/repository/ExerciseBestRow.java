package com.fitsync.workout.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * The best estimated single of one exercise before a moment, read as max(weight × (30 + reps)) so
 * the query needs no division; {@link #e1rm()} divides by 30.
 */
public record ExerciseBestRow(UUID exerciseId, BigDecimal weightTimesThirtyPlusReps) {

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    public BigDecimal e1rm() {
        return weightTimesThirtyPlusReps == null ? null : weightTimesThirtyPlusReps.divide(THIRTY, 4, RoundingMode.HALF_UP);
    }
}
