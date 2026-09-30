package com.fitsync.workout.domain;

/**
 * What a single calendar day looks like in the weekly training insight: one of the four training
 * kinds a session or plan day can carry, or one of two states for a day that has no training.
 */
public enum DayKind {
    STRENGTH,
    CARDIO,
    MOBILITY,
    SPORT,
    REST,
    OPEN
}
