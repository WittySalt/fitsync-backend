package com.fitsync.workout.dto;

import java.util.List;

public record ExerciseCatalogSummaryResponse(
        long total,
        List<ExerciseTypeCount> byType) {

    public ExerciseCatalogSummaryResponse {
        byType = byType == null ? List.of() : List.copyOf(byType);
    }

    @Override
    public List<ExerciseTypeCount> byType() {
        return List.copyOf(byType);
    }

    public record ExerciseTypeCount(String type, long count) {
    }
}
