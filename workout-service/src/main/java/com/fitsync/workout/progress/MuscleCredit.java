package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.fitsync.workout.domain.MuscleGroup;

/**
 * The hard-set rule the muscle figures share, on Progress and on a plan: a hard set counts 1 for the
 * exercise's primary group and 0.5 for each of its secondary groups, over the seven groups the
 * catalogue tracks (FULL_BODY and CARDIO are not muscle groups here).
 */
public final class MuscleCredit {

    /** The seven groups the catalogue tracks, in the order the figures list them. */
    public static final List<MuscleGroup> GROUPS = List.of(MuscleGroup.CHEST, MuscleGroup.BACK,
            MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.CORE, MuscleGroup.GLUTES, MuscleGroup.LEGS);

    public static final BigDecimal SECONDARY_SHARE = new BigDecimal("0.5");

    private MuscleCredit() {
    }

    /**
     * Credits {@code sets} hard sets of one exercise: the full count to its primary group, half to each
     * secondary group other than the primary. Groups outside the seven get nothing.
     */
    public static void credit(MuscleGroup primary, Set<MuscleGroup> secondary, BigDecimal sets,
                              BiConsumer<MuscleGroup, BigDecimal> sink) {
        if (sets == null || sets.signum() <= 0) {
            return;
        }
        if (GROUPS.contains(primary)) {
            sink.accept(primary, sets);
        }
        if (secondary == null) {
            return;
        }
        for (MuscleGroup group : GROUPS) {
            if (secondary.contains(group) && group != primary) {
                sink.accept(group, sets.multiply(SECONDARY_SHARE));
            }
        }
    }
}
