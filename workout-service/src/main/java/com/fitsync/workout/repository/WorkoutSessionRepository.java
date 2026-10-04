package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSessionEntity, UUID>, WorkoutSessionRepositoryCustom {

    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    Optional<WorkoutSessionEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<WorkoutSessionEntity> findByUserIdAndSourceAndExternalId(UUID userId, WorkoutSource source, String externalId);

    // No @EntityGraph here: findTop applies maxResults=1, and combining LIMIT with a collection
    // fetch triggers Hibernate in-memory pagination (HHH90003004). Callers re-fetch the full
    // graph by id via findBySessionIdAndUserId.
    Optional<WorkoutSessionEntity> findTopByUserIdAndStatusOrderByCompletedAtDesc(UUID userId, WorkoutSessionStatus status);

    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    List<WorkoutSessionEntity> findByUserIdOrderByStartedAtDesc(UUID userId);

    /** The person's latest finished sessions of a kind, for the effort estimate; deleted ones are left out. */
    List<WorkoutSessionEntity> findTop4ByUserIdAndKindAndStatusAndSessionIdNotOrderByStartedAtDesc(
            UUID userId, SessionKind kind, WorkoutSessionStatus status, UUID sessionId);

    /**
     * Removes every session of a person, deleted ones included, with their exercises and sets by the
     * cascade of the foreign keys. Only the final purge of an account uses it.
     */
    @Modifying
    @Query(value = "delete from workout_sessions where user_id = :userId", nativeQuery = true)
    int purgeAllByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    @Query("""
            select distinct session from WorkoutSessionEntity session
            where session.userId = :userId
              and session.sessionId in :sessionIds
            """)
    List<WorkoutSessionEntity> findHistoryPageByIds(@Param("userId") UUID userId,
                                                    @Param("sessionIds") List<UUID> sessionIds);

    // Single-query load for the weekly training insight window. All four parameters are always
    // supplied (never null): a query built with optional/nullable bind parameters hid a production
    // bug on PostgreSQL where Hibernate sends an untyped null parameter, which H2 does not reproduce.
    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    List<WorkoutSessionEntity> findByUserIdAndStatusAndStartedAtBetween(UUID userId,
                                                                        WorkoutSessionStatus status,
                                                                        Instant from,
                                                                        Instant to);

    // Single-query load for the day-by-day, today and next-training window. No exercise-log fetch
    // here: that data is never used for this calculation, so it is left out to keep the query cheap
    // over the wider (up to 90-day) window it runs against.
    @Query("""
            select session from WorkoutSessionEntity session
            where session.userId = :userId
              and session.status in :statuses
              and session.startedAt between :from and :to
            """)
    List<WorkoutSessionEntity> findByUserIdAndStatusInAndStartedAtBetween(@Param("userId") UUID userId,
                                                                          @Param("statuses") List<WorkoutSessionStatus> statuses,
                                                                          @Param("from") Instant from,
                                                                          @Param("to") Instant to);

    // The caller's in-progress sessions, newest first, so the first is the active one. Bounded by the
    // page, and without the exercise graph, which nothing that reads it needs.
    @Query("""
            select session from WorkoutSessionEntity session
            where session.userId = :userId
              and session.status = :status
            order by session.startedAt desc, session.sessionId desc
            """)
    List<WorkoutSessionEntity> findOpenSessions(@Param("userId") UUID userId,
                                                @Param("status") WorkoutSessionStatus status,
                                                Pageable pageable);

    // One page of the in-progress sessions the sweep has to look at, across all users: those that
    // started before the cutoff (the only ones that can be idle for long enough) and those that
    // have a newer in-progress session of the same user (only one may stay open). Keyset paging by
    // (startedAt, sessionId), so a session that fails to close does not come back in the same run.
    @Query("""
            select session from WorkoutSessionEntity session
            where session.status = :status
              and (session.startedAt < :cutoff
                   or exists (select 1 from WorkoutSessionEntity newer
                              where newer.userId = session.userId
                                and newer.status = :status
                                and (newer.startedAt > session.startedAt
                                     or (newer.startedAt = session.startedAt
                                         and newer.sessionId > session.sessionId))))
              and (session.startedAt > :afterStartedAt
                   or (session.startedAt = :afterStartedAt and session.sessionId > :afterSessionId))
            order by session.startedAt asc, session.sessionId asc
            """)
    List<WorkoutSessionEntity> findSweepCandidates(@Param("status") WorkoutSessionStatus status,
                                                   @Param("cutoff") Instant cutoff,
                                                   @Param("afterStartedAt") Instant afterStartedAt,
                                                   @Param("afterSessionId") UUID afterSessionId,
                                                   Pageable pageable);

    // The earliest moment any completed session of the user is dated by, using the same
    // completedAt-then-startedAt rule the insights use to place a session on a day. The parameters
    // are never null, and the result is null when the user has no completed session.
    @Query("""
            select min(coalesce(session.completedAt, session.startedAt)) from WorkoutSessionEntity session
            where session.userId = :userId
              and session.status = :status
            """)
    Instant findEarliestDatingInstant(@Param("userId") UUID userId,
                                      @Param("status") WorkoutSessionStatus status);
}
