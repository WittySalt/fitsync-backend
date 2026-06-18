package com.fitsync.user.dto;

import java.time.Instant;
import java.util.List;

public record AccountDataExportPayload(
        Instant generatedAt,
        CurrentUserResponse account,
        List<UserGoalView> goals,
        List<BodyMetricView> bodyMetrics) {

    public AccountDataExportPayload {
        goals = goals == null ? List.of() : List.copyOf(goals);
        bodyMetrics = bodyMetrics == null ? List.of() : List.copyOf(bodyMetrics);
    }

    public List<UserGoalView> goals() {
        return List.copyOf(goals);
    }

    public List<BodyMetricView> bodyMetrics() {
        return List.copyOf(bodyMetrics);
    }
}
