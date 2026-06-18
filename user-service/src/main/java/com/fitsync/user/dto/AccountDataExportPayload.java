package com.fitsync.user.dto;

import java.time.Instant;
import java.util.List;

public record AccountDataExportPayload(
        Instant generatedAt,
        CurrentUserResponse account,
        List<UserGoalView> goals,
        List<BodyMetricView> bodyMetrics) {
}
