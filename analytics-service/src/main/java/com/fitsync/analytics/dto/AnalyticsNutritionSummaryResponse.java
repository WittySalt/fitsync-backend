package com.fitsync.analytics.dto;

import java.time.LocalDate;

public record AnalyticsNutritionSummaryResponse(
        LocalDate from,
        LocalDate to,
        int totalCaloriesConsumed,
        Double totalProteinGrams,
        Double totalCarbsGrams,
        Double totalFatGrams,
        Integer totalCalorieTarget,
        Double totalProteinTargetGrams,
        Double averageCalorieAdherenceRatio,
        Double averageProteinAdherenceScore,
        int daysWithinCalorieTargetBand) {
}
