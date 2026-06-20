package com.fitsync.workout.service;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExerciseCatalogAutoImportRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExerciseCatalogAutoImportRunner.class);

    private final ExerciseCatalogImportService exerciseCatalogImportService;
    private final boolean autoImportEnabled;

    public ExerciseCatalogAutoImportRunner(
            ExerciseCatalogImportService exerciseCatalogImportService,
            @Value("${fitsync.workout.catalog.auto-import:true}") boolean autoImportEnabled) {
        this.exerciseCatalogImportService = exerciseCatalogImportService;
        this.autoImportEnabled = autoImportEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImportEnabled) {
            LOGGER.info("free-exercise-db catalog auto import is disabled");
            return;
        }

        if (exerciseCatalogImportService.isFreeExerciseDbCatalogPresent()) {
            LOGGER.info("free-exercise-db catalog already present, skipping auto import");
            return;
        }

        try {
            ExerciseCatalogImportResponse response = exerciseCatalogImportService.importFreeExerciseDbCatalog();
            LOGGER.info(
                    "free-exercise-db catalog auto import completed: processed={}, created={}, updated={}, rejected={}",
                    response.processed(),
                    response.created(),
                    response.updated(),
                    response.rejected());
        } catch (RuntimeException ex) {
            LOGGER.error("free-exercise-db catalog auto import failed", ex);
        }
    }
}
