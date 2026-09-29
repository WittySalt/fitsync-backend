package com.fitsync.workout.domain;

/**
 * Where a day's {@link DayKind} in the weekly training insight came from, in the order the
 * calculation tries them: a completed session, a session still in progress, the active plan's
 * weekly schedule, an inferred habit from recent history, or none of those.
 */
public enum DaySource {
    DONE,
    IN_PROGRESS,
    PLAN,
    HABIT,
    NONE
}
