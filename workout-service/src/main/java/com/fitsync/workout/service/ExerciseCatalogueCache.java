package com.fitsync.workout.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import com.fitsync.web.response.PageResponse;
import com.fitsync.workout.config.CatalogueCacheConfig;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseView;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cache-aside for searches of the shared exercise catalogue, which is the same for every user.
 *
 * <p>Only a search that can contain catalogue rows alone comes here: the loader always queries as
 * {@link #CATALOGUE_ONLY_OWNER}, an owner no exercise has, so a cached page can never hold anyone's own
 * exercise. A caller with exercises of their own is answered from the database instead. The key names every
 * search parameter, so two searches share an entry only when they are the same search.
 */
@Component
public class ExerciseCatalogueCache {

    /** Owns no exercise, so the accessible-exercises query returns the catalogue only. */
    static final UUID CATALOGUE_ONLY_OWNER = new UUID(0L, 0L);

    @Cacheable(cacheNames = CatalogueCacheConfig.EXERCISE_SEARCH_CACHE, key = "#key")
    public PageResponse<ExerciseView> search(String key, Supplier<PageResponse<ExerciseView>> loader) {
        return loader.get();
    }

    /**
     * Every parameter of the search. The text is lower-cased, as the search itself matches it, and URL-encoded so
     * that no value can run into the next one.
     */
    static String key(String query,
                      ExerciseType exerciseType,
                      MuscleGroup primaryMuscleGroup,
                      ExerciseEquipment equipment,
                      WorkoutDifficulty difficulty,
                      int page,
                      int size) {
        return String.join("|",
                "v1",
                "q=" + (query == null ? "" : URLEncoder.encode(query.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8)),
                "type=" + exerciseType,
                "muscle=" + primaryMuscleGroup,
                "equipment=" + equipment,
                "difficulty=" + difficulty,
                "page=" + page,
                "size=" + size);
    }
}
