package com.fitsync.workout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSessionEntity, UUID>, WorkoutSessionRepositoryCustom {

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutSessionEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<WorkoutSessionEntity> findByUserIdAndSourceAndExternalId(UUID userId, WorkoutSource source, String externalId);

    // No @EntityGraph here: findTop applies maxResults=1, and combining LIMIT with a collection
    // fetch triggers Hibernate in-memory pagination (HHH90003004). Callers re-fetch the full
    // graph by id via findBySessionIdAndUserId.
    Optional<WorkoutSessionEntity> findTopByUserIdAndStatusOrderByCompletedAtDesc(UUID userId, WorkoutSessionStatus status);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    List<WorkoutSessionEntity> findByUserIdOrderByStartedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    @Query("""
            select distinct session from WorkoutSessionEntity session
            where session.userId = :userId
              and session.sessionId in :sessionIds
            """)
    List<WorkoutSessionEntity> findHistoryPageByIds(@Param("userId") UUID userId,
                                                    @Param("sessionIds") List<UUID> sessionIds);
}
