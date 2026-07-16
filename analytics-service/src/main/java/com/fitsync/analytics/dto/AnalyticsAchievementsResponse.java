package com.fitsync.analytics.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsAchievementsResponse(List<AchievementView> achievements) {

    public AnalyticsAchievementsResponse {
        achievements = List.copyOf(achievements);
    }

    @Override
    public List<AchievementView> achievements() {
        return List.copyOf(achievements);
    }

    public record AchievementView(
            String code,
            String title,
            boolean unlocked,
            Instant unlockedAt,
            double progress) {
    }
}
