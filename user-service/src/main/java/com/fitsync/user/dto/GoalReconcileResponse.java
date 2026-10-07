package com.fitsync.user.dto;

/** publishedCount is the active goals republished, usersTouched the distinct users they belong to. */
public record GoalReconcileResponse(
        int publishedCount,
        int usersTouched
) {
}
