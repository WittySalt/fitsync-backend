package com.fitsync.workout.service;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;

public interface ExerciseCatalogImportService {

    ExerciseCatalogImportResponse importFreeExerciseDbCatalog();

    /**
     * @return true if the free-exercise-db seed catalog has already been imported,
     *         so the startup auto-import can be skipped.
     */
    boolean isFreeExerciseDbCatalogPresent();
}
