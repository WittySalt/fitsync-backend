package com.fitsync.workout.dto;

import java.util.List;

public record ImportWorkoutSessionsResponse(
        int imported,
        int skippedDuplicate,
        int rejected,
        List<ImportWorkoutSessionRowResult> results) {

    public ImportWorkoutSessionsResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    @Override
    public List<ImportWorkoutSessionRowResult> results() {
        return List.copyOf(results);
    }
}
