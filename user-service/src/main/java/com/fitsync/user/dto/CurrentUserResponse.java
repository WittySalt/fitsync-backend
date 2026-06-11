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

    public CurrentUserResponse {
        activeGoals = activeGoals == null ? List.of() : List.copyOf(activeGoals);
    }

    @Override
    public List<UserGoalView> activeGoals() {
        return List.copyOf(activeGoals);
    }
}
