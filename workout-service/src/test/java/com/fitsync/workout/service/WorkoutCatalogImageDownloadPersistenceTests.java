package com.fitsync.workout.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.media.MediaStorageService;
import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=",
        "fitsync.workout.catalog.free-exercise-db.download-when-missing=true"
})
@ActiveProfiles("test")
class WorkoutCatalogImageDownloadPersistenceTests {

    @Autowired
    private ExerciseCatalogImportService exerciseCatalogImportService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private UpstreamHttpImageSource upstreamHttpImageSource;

    private final CurrentUserPrincipal currentUser = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com",
            "Alice",
            "USER");

    @BeforeEach
    void clearExercises() {
        deleteImportedFixture("Fixture_Bench_Press");
        deleteImportedFixture("Fixture_Fallback_Move");
        when(upstreamHttpImageSource.isAvailable()).thenReturn(true);
    }

    @Test
    void shouldUseUpstreamImageSourceWhenLocalDirectoryIsNotConfiguredAndRemainIdempotent() {
        when(upstreamHttpImageSource.load("Fixture_Bench_Press/0.data"))
                .thenReturn(Optional.of(new CatalogImage("zero".getBytes(StandardCharsets.UTF_8), "image/jpeg")));
        when(upstreamHttpImageSource.load("Fixture_Bench_Press/1.data"))
                .thenReturn(Optional.of(new CatalogImage("one".getBytes(StandardCharsets.UTF_8), "image/jpeg")));
        when(mediaStorageService.put(eq("exercises/Fixture_Bench_Press/0.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/0.jpg");
        when(mediaStorageService.put(eq("exercises/Fixture_Bench_Press/1.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/1.jpg");

        ExerciseCatalogImportResponse first = exerciseCatalogImportService.importFreeExerciseDbCatalog();
        ExerciseCatalogImportResponse second = exerciseCatalogImportService.importFreeExerciseDbCatalog();

        assertEquals(2, first.created());
        assertEquals(0, first.rejected());
        assertEquals(0, second.created());
        assertEquals(2, second.updated());
        ExerciseView bench = importedBenchPress();
        assertEquals(2, bench.images().size());
        assertEquals("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/0.jpg", bench.images().get(0).url());
        verify(upstreamHttpImageSource, times(2)).load("Fixture_Bench_Press/0.data");
        verify(upstreamHttpImageSource, times(2)).load("Fixture_Bench_Press/1.data");
        verify(mediaStorageService, times(2))
                .put(eq("exercises/Fixture_Bench_Press/0.jpg"), any(byte[].class), eq("image/jpeg"));
        verify(mediaStorageService, times(2))
                .put(eq("exercises/Fixture_Bench_Press/1.jpg"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void shouldSkipSingleFailedUpstreamImageWithoutRejectingCatalogRow() {
        when(upstreamHttpImageSource.load("Fixture_Bench_Press/0.data")).thenReturn(Optional.empty());
        when(upstreamHttpImageSource.load("Fixture_Bench_Press/1.data"))
                .thenReturn(Optional.of(new CatalogImage("one".getBytes(StandardCharsets.UTF_8), "image/jpeg")));
        when(mediaStorageService.put(eq("exercises/Fixture_Bench_Press/1.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/1.jpg");

        ExerciseCatalogImportResponse response = exerciseCatalogImportService.importFreeExerciseDbCatalog();

        assertEquals(2, response.created());
        assertEquals(0, response.rejected());
        ExerciseView bench = importedBenchPress();
        assertEquals(1, bench.images().size());
        assertEquals(1, bench.images().get(0).position());
        assertEquals("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/1.jpg", bench.images().get(0).url());
    }

    private ExerciseView importedBenchPress() {
        ExerciseEntity bench = exerciseRepository
                .findByExternalSourceAndExternalRef("free-exercise-db", "Fixture_Bench_Press")
                .orElseThrow();
        return exerciseService.getExercise(currentUser, bench.getExerciseId());
    }

    private void deleteImportedFixture(String externalRef) {
        exerciseRepository.findByExternalSourceAndExternalRef("free-exercise-db", externalRef)
                .ifPresent(exerciseRepository::delete);
    }
}
