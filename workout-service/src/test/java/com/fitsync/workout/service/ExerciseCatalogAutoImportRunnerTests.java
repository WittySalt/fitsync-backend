package com.fitsync.workout.service;

import java.util.List;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseCatalogAutoImportRunnerTests {

    @Mock
    private ExerciseCatalogImportService exerciseCatalogImportService;

    @Test
    void shouldImportCatalogWhenAutoImportIsEnabled() {
        when(exerciseCatalogImportService.importFreeExerciseDbCatalog())
                .thenReturn(new ExerciseCatalogImportResponse(2, 1, 1, 0, List.of()));
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true,
                Runnable::run);

        runner.run(new DefaultApplicationArguments());

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }

    @Test
    void shouldSkipCatalogImportWhenCatalogAndItsImagesArePresent() {
        when(exerciseCatalogImportService.isFreeExerciseDbCatalogPresent()).thenReturn(true);
        when(exerciseCatalogImportService.isFreeExerciseDbImageSetPresent()).thenReturn(true);
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true,
                Runnable::run);

        runner.run(new DefaultApplicationArguments());

        verify(exerciseCatalogImportService).isFreeExerciseDbCatalogPresent();
        verify(exerciseCatalogImportService, never()).importFreeExerciseDbCatalog();
    }

    @Test
    void shouldReimportWhenTheCatalogIsPresentButHasNoImagesAtAll() {
        // A first boot whose image source was unusable leaves every exercise without images;
        // without this the runner would see "catalog present" and never try again.
        when(exerciseCatalogImportService.isFreeExerciseDbCatalogPresent()).thenReturn(true);
        when(exerciseCatalogImportService.isFreeExerciseDbImageSetPresent()).thenReturn(false);
        when(exerciseCatalogImportService.importFreeExerciseDbCatalog())
                .thenReturn(new ExerciseCatalogImportResponse(876, 0, 876, 0, List.of()));
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true,
                Runnable::run);

        runner.run(new DefaultApplicationArguments());

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }

    @Test
    void runsTheImportOnTheExecutorSoStartupIsNotHeldUp() {
        java.util.List<Runnable> submitted = new java.util.ArrayList<>();
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true,
                submitted::add);

        runner.run(new DefaultApplicationArguments());

        // run() has returned and the import has only been handed to the executor.
        verify(exerciseCatalogImportService, never()).importFreeExerciseDbCatalog();
        org.assertj.core.api.Assertions.assertThat(submitted).hasSize(1);

        when(exerciseCatalogImportService.importFreeExerciseDbCatalog())
                .thenReturn(new ExerciseCatalogImportResponse(1, 1, 0, 0, List.of()));
        submitted.get(0).run();
        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }

    @Test
    void shouldSkipCatalogImportWhenAutoImportIsDisabled() {
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                false,
                Runnable::run);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(exerciseCatalogImportService);
    }

    @Test
    void shouldNotBlockStartupWhenAutoImportFails() {
        when(exerciseCatalogImportService.importFreeExerciseDbCatalog())
                .thenThrow(new IllegalStateException("storage unavailable"));
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true,
                Runnable::run);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }
}
