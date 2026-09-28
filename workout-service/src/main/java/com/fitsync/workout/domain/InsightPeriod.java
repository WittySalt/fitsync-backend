package com.fitsync.workout.domain;

/** Which calendar week the training page insight is reporting on, or {@code NONE} when neither
 * the current nor the previous week has any completed session to report. */
public enum InsightPeriod {
    THIS_WEEK,
    PREVIOUS_WEEK,
    NONE
}
