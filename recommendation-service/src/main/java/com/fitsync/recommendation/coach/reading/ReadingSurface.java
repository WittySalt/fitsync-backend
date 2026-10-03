package com.fitsync.recommendation.coach.reading;

/** Which page an insight panel's reading belongs to. Stored by name, so a case is never renamed. */
public enum ReadingSurface {
    WORKOUT,
    NUTRITION,
    PROGRESS,
    /** A weekly review on the Progress page, one row per Monday week. */
    PROGRESS_WEEK
}
