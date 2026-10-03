package com.fitsync.recommendation.coach.reading;

/** What a client should show for one week's review. */
public enum WeeklyReviewStatus {
    /** A review is attached, written by fixed rules or by the model once it passed the check. */
    READY,
    /** The rules had nothing to say and a model review is being written; ask again shortly. */
    WRITING,
    /** The week has fewer than two facts, or its figures could not be read. */
    NONE
}
