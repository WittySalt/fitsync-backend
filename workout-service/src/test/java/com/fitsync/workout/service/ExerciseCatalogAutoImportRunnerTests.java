package com.fitsync.workout.service;

import java.util.List;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
                true);

        runner.run(new DefaultApplicationArguments());

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }

    @Test
    void shouldSkipCatalogImportWhenAutoImportIsDisabled() {
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                false);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(exerciseCatalogImportService);
    }

    @Test
    void shouldNotBlockStartupWhenAutoImportFails() {
        when(exerciseCatalogImportService.importFreeExerciseDbCatalog())
                .thenThrow(new IllegalStateException("storage unavailable"));
        ExerciseCatalogAutoImportRunner runner = new ExerciseCatalogAutoImportRunner(
                exerciseCatalogImportService,
                true);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
    }
}
