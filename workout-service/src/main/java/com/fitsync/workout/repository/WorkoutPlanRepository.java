package com.fitsync.workout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, UUID>, WorkoutPlanRepositoryCustom {

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutPlanEntity> findByPlanIdAndUserId(UUID planId, UUID userId);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    List<WorkoutPlanEntity> findByUserIdOrderByDateTimeUpdatedDesc(UUID userId);

    List<WorkoutPlanEntity> findByUserIdAndStatusOrderByDateTimeUpdatedDesc(UUID userId, WorkoutPlanStatus status);

    List<WorkoutPlanEntity> findByUserIdAndStatus(UUID userId, WorkoutPlanStatus status);
}
