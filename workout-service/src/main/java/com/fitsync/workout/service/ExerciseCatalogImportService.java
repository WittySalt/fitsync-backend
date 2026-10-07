package com.fitsync.workout.service;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;

public interface ExerciseCatalogImportService {

    ExerciseCatalogImportResponse importFreeExerciseDbCatalog();

    /**
     * @return true if the free-exercise-db seed catalog has already been imported,
     *         so the startup auto-import can be skipped.
     */
    boolean isFreeExerciseDbCatalogPresent();

    /**
     * @return true if at least one free-exercise-db exercise has an image. False after a
     *         first import whose image source was unusable, which the auto-import repairs.
     */
    boolean isFreeExerciseDbImageSetPresent();
}
