package com.fitsync.notification.domain;

/** The emails of the spec, by their number there. */
public enum EmailKind {
    /** E1, today's training, an hour before it. */
    TRAINING_TODAY,
    /** E4, a weight goal reached. */
    GOAL_REACHED,
    /** E5, last week in numbers. */
    WEEKLY_REPORT,
    /** E6, the account is scheduled for deletion. Sent whatever the settings say. */
    DELETION_SCHEDULED
}
