package com.fitsync.workout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
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
}
