package com.fitsync.workout.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<ExerciseEntity, UUID> {

    boolean existsByCode(String code);

    @Query("""
            select exercise from ExerciseEntity exercise
            where exercise.exerciseId = :exerciseId
              and exercise.status = :status
              and (exercise.ownerUserId is null or exercise.ownerUserId = :userId)
            """)
    Optional<ExerciseEntity> findAccessibleById(@Param("exerciseId") UUID exerciseId,
                                                @Param("userId") UUID userId,
                                                @Param("status") ExerciseStatus status);

    @Query("""
            select exercise from ExerciseEntity exercise
            where exercise.status = :status
              and (exercise.ownerUserId is null or exercise.ownerUserId = :userId)
              and (:query is null
                   or lower(exercise.name) like lower(concat('%', :query, '%'))
                   or lower(exercise.code) like lower(concat('%', :query, '%')))
              and (:exerciseType is null or exercise.exerciseType = :exerciseType)
              and (:primaryMuscleGroup is null or exercise.primaryMuscleGroup = :primaryMuscleGroup)
              and (:equipment is null or exercise.equipment = :equipment)
              and (:difficulty is null or exercise.difficulty = :difficulty)
            """)
    Page<ExerciseEntity> searchAccessible(@Param("userId") UUID userId,
                                          @Param("status") ExerciseStatus status,
                                          @Param("query") String query,
                                          @Param("exerciseType") ExerciseType exerciseType,
                                          @Param("primaryMuscleGroup") MuscleGroup primaryMuscleGroup,
                                          @Param("equipment") ExerciseEquipment equipment,
                                          @Param("difficulty") WorkoutDifficulty difficulty,
                                          Pageable pageable);
}
