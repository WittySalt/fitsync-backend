package com.fitsync.workout.progress;

import java.math.BigDecimal;

/** One performed set: logged, not skipped. Weight or reps may be missing. */
public record SetSnap(BigDecimal weightKg, Integer reps) {

    /** A set counts as hard work when it carries a logged weight or reps. */
    public boolean hasWork() {
        return weightKg != null || reps != null;
    }

    /** The estimated best single of this set, or null without a positive weight and positive reps. */
    public BigDecimal e1rm() {
        if (weightKg == null || reps == null || reps <= 0 || weightKg.signum() <= 0) {
            return null;
        }
        return StrengthMath.e1rm(weightKg, reps);
    }

    /** Weight times reps, zero when either is missing. */
    public BigDecimal volume() {
        if (weightKg == null || reps == null || reps <= 0) {
            return BigDecimal.ZERO;
        }
        return weightKg.multiply(BigDecimal.valueOf(reps));
    }
}
