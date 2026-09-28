package com.fitsync.recommendation.coach.reading;

/** What a client should show for a page's reading right now. */
public enum ReadingStatus {
    /**
     * A reading is attached, written by fixed rules by default, or by the model once one has been
     * generated and has passed validation for exactly these facts.
     */
    READY,
    /**
     * The fixed rules found nothing to say for these facts and a model generation is in flight; the
     * client should poll again shortly.
     */
    WRITING,
    /**
     * The fixed rules found nothing to say for these facts, and either no model generation is
     * configured or the model already failed the check for these exact facts, so nothing will be
     * retried until any rejection expires.
     */
    RESTING,
    /** Too little happened in the period to say anything, or there is no period at all. */
    EMPTY
}
