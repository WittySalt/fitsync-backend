package com.fitsync.workout.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, UUID> {

    @Query("""
            select plan from WorkoutPlanEntity plan
            where plan.userId = :userId
              and (:status is null or plan.status = :status)
            """)
    Page<WorkoutPlanEntity> findByUserIdAndOptionalStatus(@Param("userId") UUID userId,
                                                          @Param("status") WorkoutPlanStatus status,
                                                          Pageable pageable);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutPlanEntity> findByPlanIdAndUserId(UUID planId, UUID userId);
}
