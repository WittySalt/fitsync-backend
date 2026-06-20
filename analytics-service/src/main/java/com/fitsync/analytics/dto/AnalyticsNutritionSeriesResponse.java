package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsNutritionSeriesResponse(
        LocalDate from,
        LocalDate to,
        String bucket,
        List<NutritionSeriesPoint> points) {

    public AnalyticsNutritionSeriesResponse {
        points = List.copyOf(points);
    }

    @Override
    public List<NutritionSeriesPoint> points() {
        return List.copyOf(points);
    }

    public record NutritionSeriesPoint(
            LocalDate date,
            int totalCaloriesConsumed,
            Double totalProteinGrams,
            Double totalCarbsGrams,
            Double totalFatGrams,
            Integer calorieTarget,
            Double proteinTargetGrams) {
    }
}
