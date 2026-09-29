package com.fitsync.workout.domain;

/**
 * The kind of a workout session, as introduced by the coach day-kinds spec (K1).
 * Every session that is created or migrated by V8 is assigned exactly one of these.
 */
public enum SessionKind {
    STRENGTH,
    CARDIO,
    MOBILITY,
    SPORT
}
