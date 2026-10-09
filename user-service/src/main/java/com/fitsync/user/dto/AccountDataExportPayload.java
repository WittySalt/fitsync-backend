package com.fitsync.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * The person's copy of what user-service keeps about them. The other services export their own part
 * ({@code relatedExports}), and the app puts the parts together into one download.
 *
 * @param healthReadiness the readiness answers, or null when the person never gave any
 * @param relatedExports  the gateway paths of the other services' exports for the same person
 */
public record AccountDataExportPayload(
        Instant generatedAt,
        CurrentUserResponse account,
        List<UserGoalView> goals,
        List<BodyMetricView> bodyMetrics,
        HealthReadinessView healthReadiness,
        List<String> relatedExports) {

    public AccountDataExportPayload {
        goals = goals == null ? List.of() : List.copyOf(goals);
        bodyMetrics = bodyMetrics == null ? List.of() : List.copyOf(bodyMetrics);
        relatedExports = relatedExports == null ? List.of() : List.copyOf(relatedExports);
    }

    public List<UserGoalView> goals() {
        return List.copyOf(goals);
    }

    public List<BodyMetricView> bodyMetrics() {
        return List.copyOf(bodyMetrics);
    }

    public List<String> relatedExports() {
        return List.copyOf(relatedExports);
    }
}
