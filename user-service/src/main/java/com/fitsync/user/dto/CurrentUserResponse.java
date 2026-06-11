package com.fitsync.user.dto;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String email,
        String role,
        UserProfileView profile,
        UserPreferencesView preferences,
        List<UserGoalView> activeGoals,
        BodyMetricView latestBodyMetrics) {
}
