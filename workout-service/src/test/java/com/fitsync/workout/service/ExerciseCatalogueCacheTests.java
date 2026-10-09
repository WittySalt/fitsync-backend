package com.fitsync.workout.service;

import java.util.List;
import java.util.UUID;

import com.fitsync.media.MediaStorageService;
import com.fitsync.web.response.PageResponse;
import com.fitsync.workout.config.CatalogueCacheConfig;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue search cache with an in-memory store standing in for Redis; the Redis wiring itself is covered
 * by {@code CatalogueCacheSerializationTests}. A database of its own, so the import here cannot disturb the
 * persistence tests that import the same fixtures.
 */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "spring.datasource.url=jdbc:h2:mem:workout_catalogue_cache;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS workout;DB_CLOSE_DELAY=-1",
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
class ExerciseCatalogueCacheTests {

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExerciseCatalogImportService exerciseCatalogImportService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private UpstreamHttpImageSource upstreamHttpImageSource;

    private Cache cache;

    @BeforeEach
    void clearCache() {
        cache = cacheManager.getCache(CatalogueCacheConfig.EXERCISE_SEARCH_CACHE);
        assertThat(cache).isNotNull();
        cache.clear();
    }

    @Test
    void theSecondIdenticalSearchIsAnsweredFromTheCacheForAnyUserWithoutOwnExercises() {
        String name = "Cache Probe " + UUID.randomUUID();
        PageResponse<ExerciseView> first = search(newUser(), name, 0);
        saveCatalogueExercise(name);

        PageResponse<ExerciseView> second = search(newUser(), name, 0);

        assertThat(first.content()).isEmpty();
        // The row written straight to the database is not seen: the answer came from the cache.
        assertThat(second).isEqualTo(first);
        assertThat(cache.get(key(name, 0))).isNotNull();
    }

    @Test
    void aDifferentSearchMissesTheCache() {
        String name = "Cache Miss " + UUID.randomUUID();
        search(newUser(), name, 0);
        saveCatalogueExercise(name);

        PageResponse<ExerciseView> otherPage = search(newUser(), name, 1);
        PageResponse<ExerciseView> otherFilter = exerciseService.listExercises(newUser(), name, ExerciseType.STRENGTH,
                null, null, null, 0, 20);

        assertThat(otherPage.totalElements()).isEqualTo(1);
        assertThat(otherFilter.content()).extracting(ExerciseView::name).containsExactly(name);
        assertThat(cache.get(key(name, 1))).isNotNull();
    }

    @Test
    void aUserWithOwnExercisesIsAnsweredFromTheDatabaseAndNeverSharesThem() {
        CurrentUserPrincipal owner = newUser();
        String name = "Own Lift " + UUID.randomUUID();
        exerciseService.createCustomExercise(owner, new CreateExerciseRequest(
                "own-" + UUID.randomUUID(), name, "Custom", ExerciseType.STRENGTH, MuscleGroup.BACK,
                ExerciseEquipment.BARBELL, WorkoutDifficulty.BEGINNER));

        PageResponse<ExerciseView> ownersView = search(owner, name, 0);
        PageResponse<ExerciseView> someoneElsesView = search(newUser(), name, 0);

        assertThat(ownersView.content()).extracting(ExerciseView::name).containsExactly(name);
        assertThat(someoneElsesView.content()).isEmpty();
    }

    @Test
    void theCatalogueImportEvictsEveryCachedSearch() {
        PageResponse<ExerciseView> before = search(newUser(), "Fixture Fallback", 0);

        exerciseCatalogImportService.importFreeExerciseDbCatalog();
        PageResponse<ExerciseView> after = search(newUser(), "Fixture Fallback", 0);

        assertThat(before.content()).isEmpty();
        assertThat(after.content()).extracting(ExerciseView::name).contains("Fixture Fallback Move");
    }

    private PageResponse<ExerciseView> search(CurrentUserPrincipal user, String query, int page) {
        return exerciseService.listExercises(user, query, null, null, null, null, page, 20);
    }

    private static String key(String query, int page) {
        return ExerciseCatalogueCache.key(query, null, null, null, null, page, 20);
    }

    private void saveCatalogueExercise(String name) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode("catalogue-" + UUID.randomUUID());
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        entity.setInstructions(new java.util.ArrayList<>());
        entity.setImages(new java.util.ArrayList<>());
        entity.setSecondaryMuscles(new java.util.ArrayList<>(List.of()));
        exerciseRepository.saveAndFlush(entity);
    }

    private static CurrentUserPrincipal newUser() {
        return new CurrentUserPrincipal(UUID.randomUUID(), "user@example.com", "User", "USER");
    }
}
