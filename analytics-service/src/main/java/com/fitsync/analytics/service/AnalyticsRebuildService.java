package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsRebuildResponse;

public interface AnalyticsRebuildService {

    AnalyticsRebuildResponse rebuildDashboard(UUID userId);

    AnalyticsRebuildResponse rebuildNutrition(UUID userId, LocalDate date);

    AnalyticsRebuildResponse rebuildWorkouts(UUID userId, LocalDate date);
}
