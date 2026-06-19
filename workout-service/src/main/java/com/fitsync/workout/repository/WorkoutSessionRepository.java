package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSessionEntity, UUID> {

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutSessionEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<WorkoutSessionEntity> findTopByUserIdOrderByStartedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutSessionEntity> findTopByUserIdAndStatusOrderByStartedAtDesc(UUID userId, WorkoutSessionStatus status);

    @Query("""
            select session from WorkoutSessionEntity session
            where session.userId = :userId
              and (:from is null or session.startedAt >= :from)
              and (:to is null or session.startedAt <= :to)
              and (:status is null or session.status = :status)
              and (:planId is null or session.planId = :planId)
            """)
    Page<WorkoutSessionEntity> findHistory(@Param("userId") UUID userId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           @Param("status") WorkoutSessionStatus status,
                                           @Param("planId") UUID planId,
                                           Pageable pageable);
}
