package com.fitsync.workout.domain;

/**
 * How a {@link SportActivity} counts toward the weekly load: as cardio, as strength,
 * or as a skill session that only counts as an active day.
 */
public enum SportEffort {
    CARDIO,
    STRENGTH,
    SKILL
}
