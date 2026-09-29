package com.fitsync.workout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseCatalogSummaryResponse;
import com.fitsync.workout.dto.ExerciseCatalogSummaryResponse.ExerciseTypeCount;
import com.fitsync.workout.entity.ExerciseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<ExerciseEntity, UUID>, ExerciseRepositoryCustom {

    boolean existsByCodeAndOwnerUserId(String code, UUID ownerUserId);

    List<ExerciseEntity> findByOwnerUserIdOrderByNameAsc(UUID ownerUserId);

    Optional<ExerciseEntity> findByExternalSourceAndExternalRef(String externalSource, String externalRef);

    long countByExternalSource(String externalSource);

    @Query("""
            select exercise from ExerciseEntity exercise
            where exercise.exerciseId = :exerciseId
              and exercise.status = :status
              and (exercise.ownerUserId is null or exercise.ownerUserId = :userId)
            """)
    Optional<ExerciseEntity> findAccessibleById(@Param("exerciseId") UUID exerciseId,
                                                @Param("userId") UUID userId,
                                                @Param("status") ExerciseStatus status);

    /**
     * Groups the shared catalogue (never a user's own custom exercises, which carry
     * {@code custom = true}) by exercise type in one aggregate pass, instead of loading every row.
     */
    @Query("""
            select new com.fitsync.workout.repository.ExerciseTypeCountRow(exercise.exerciseType, count(exercise))
            from ExerciseEntity exercise
            where exercise.custom = :custom
            group by exercise.exerciseType
            order by count(exercise) desc, exercise.exerciseType asc
            """)
    List<ExerciseTypeCountRow> countCatalogExercisesByType(@Param("custom") boolean custom);

    default ExerciseCatalogSummaryResponse summarizeCatalogByType() {
        List<ExerciseTypeCountRow> rows = countCatalogExercisesByType(false);
        List<ExerciseTypeCount> byType = rows.stream()
                .map(row -> new ExerciseTypeCount(row.exerciseType().name(), row.count()))
                .toList();
        long total = byType.stream().mapToLong(ExerciseTypeCount::count).sum();
        return new ExerciseCatalogSummaryResponse(total, byType);
    }
}
