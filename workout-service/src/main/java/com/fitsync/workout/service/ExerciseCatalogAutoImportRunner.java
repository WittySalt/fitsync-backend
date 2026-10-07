package com.fitsync.workout.service;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Seeds the free-exercise-db catalog on startup. The import itself runs on a background
 * thread: it uploads about 1700 images and takes minutes, and Spring Boot does not report
 * the service ready until every ApplicationRunner has returned, so a synchronous import kept
 * /actuator/health at 503 past the deploy's health-check timeout.
 */
@Component
public class ExerciseCatalogAutoImportRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExerciseCatalogAutoImportRunner.class);

    private final ExerciseCatalogImportService exerciseCatalogImportService;
    private final boolean autoImportEnabled;
    private final TaskExecutor taskExecutor;

    public ExerciseCatalogAutoImportRunner(
            ExerciseCatalogImportService exerciseCatalogImportService,
            @Value("${fitsync.workout.catalog.auto-import:true}") boolean autoImportEnabled,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.exerciseCatalogImportService = exerciseCatalogImportService;
        this.autoImportEnabled = autoImportEnabled;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImportEnabled) {
            LOGGER.info("free-exercise-db catalog auto import is disabled");
            return;
        }

        if (exerciseCatalogImportService.isFreeExerciseDbCatalogPresent()) {
            if (exerciseCatalogImportService.isFreeExerciseDbImageSetPresent()) {
                LOGGER.info("free-exercise-db catalog already present, skipping auto import");
                return;
            }
            // Present but without a single image: an earlier import ran with an unusable
            // image source. Re-running it fills the images in; existing rows are updated.
            LOGGER.warn("free-exercise-db catalog has no images, re-running the import to fill them in");
        }

        LOGGER.info("free-exercise-db catalog auto import started in the background");
        taskExecutor.execute(this::importCatalog);
    }

    private void importCatalog() {
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
