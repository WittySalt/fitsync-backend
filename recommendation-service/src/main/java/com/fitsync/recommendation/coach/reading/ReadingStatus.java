package com.fitsync.recommendation.coach.reading;

/** What a client should show for a page's reading right now. */
public enum ReadingStatus {
    /** A validated reading is attached. */
    READY,
    /** A generation is in flight; the client should poll again shortly. */
    WRITING,
    /** The model already failed the check for these exact facts; nothing will be retried until the rejection expires. */
    RESTING,
    /** Too little happened in the period to say anything, or there is no period at all. */
    EMPTY
}
