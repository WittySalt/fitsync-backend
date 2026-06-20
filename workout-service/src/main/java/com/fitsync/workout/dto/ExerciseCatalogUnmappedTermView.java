package com.fitsync.workout.dto;

public record ExerciseCatalogUnmappedTermView(
        String field,
        String term,
        String mappedTo,
        int count) {
}
