package com.fitsync.nutrition.domain;

/** The stretch of days an insight describes, or {@code NONE} when that stretch holds no meals. */
public enum InsightPeriod {
    PREVIOUS_WEEK,
    /** Monday of the current week to today, for the coach page reading. */
    THIS_WEEK,
    NONE
}
