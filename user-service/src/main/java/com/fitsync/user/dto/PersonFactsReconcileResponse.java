package com.fitsync.user.dto;

/** How many events a republish of the person facts put on the outbox, by kind. */
public record PersonFactsReconcileResponse(
        int referenceProfilesPublished,
        int preferencesPublished,
        int bodyMetricsPublished
) {
}
