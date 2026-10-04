package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.dto.ExerciseTopWeightRow;
import com.fitsync.workout.dto.SessionExerciseTopWeightRow;
import com.fitsync.workout.entity.SessionEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutExerciseLogRepository extends JpaRepository<SessionEntry, UUID>,
        WorkoutExerciseLogRepositoryCustom {

    // The exercise names of one session of the user, in sequence order; time segments have none. Bounded by the page, so the
    // weekly insight reads only a handful of names for the one session it points at.
    @Query("""
            select el.exerciseNameSnapshot from SessionEntry el
            where el.session.sessionId = :sessionId
              and el.session.userId = :userId
              and el.entryType = com.fitsync.workout.domain.EntryType.SET
            order by el.sequence asc
            """)
    List<String> findExerciseNames(@Param("userId") UUID userId,
                                   @Param("sessionId") UUID sessionId,
                                   Pageable pageable);

    // For each of the given sessions that has a set or a time segment marked done or skipped: when one
    // was last marked (a segment by its start) and how many are done. Sessions without such a row have
    // none. One grouped query, so closing a left workout never loads its sets just to find out when it
    // was last worked on. A segment has no sets, so the outer join gives it one row with an empty set.
    @Query("""
            select new com.fitsync.workout.repository.SessionActivityRow(
                       el.session.sessionId,
                       max(coalesce(s.updatedAt, el.startedAt)),
                       sum(case when s.status = :doneStatus then 1L
                                when el.entryType = com.fitsync.workout.domain.EntryType.SEGMENT
                                     and el.status = :doneStatus then 1L
                                else 0L end))
            from SessionEntry el
              left join el.sets s
            where el.session.sessionId in :sessionIds
              and (s.status in :activeStatuses
                   or (el.entryType = com.fitsync.workout.domain.EntryType.SEGMENT and el.status in :activeStatuses))
            group by el.session.sessionId
            """)
    List<SessionActivityRow> findSessionActivity(@Param("sessionIds") List<UUID> sessionIds,
                                                 @Param("activeStatuses") List<WorkoutSetStatus> activeStatuses,
                                                 @Param("doneStatus") WorkoutSetStatus doneStatus);

    // Bounded, grouped projection used by the training page insight's "personal record" rule: the
    // heaviest logged set weight per exercise across the user's history, without loading whole
    // session/log/set entity graphs. "Before the period" is approximated by session startedAt
    // being earlier than the period's start-of-day instant; the period membership of a session is
    // otherwise decided by completedAt's local date, but that distinction only matters for a
    // session that starts on one side of midnight and completes on the other, which this
    // approximation intentionally accepts per the endpoint's specification.
    // Only sets that were logged and not skipped count, which is the same rule the service
    // applies to the period's own sets.
    @Query("""
            select new com.fitsync.workout.dto.ExerciseTopWeightRow(el.exercise.exerciseId, max(s.weightKg))
            from SessionEntry el
              join el.sets s
              join el.session sess
            where sess.userId = :userId
              and sess.status = :status
              and sess.startedAt < :beforeInstant
              and s.weightKg is not null
              and s.logged = true
              and s.status <> :skippedStatus
            group by el.exercise.exerciseId
            """)
    List<ExerciseTopWeightRow> findMaxSetWeightPerExerciseBeforeStartedAt(@Param("userId") UUID userId,
                                                                          @Param("status") WorkoutSessionStatus status,
                                                                          @Param("skippedStatus") WorkoutSetStatus skippedStatus,
                                                                          @Param("beforeInstant") Instant beforeInstant);

    // The heaviest logged set weight of each exercise within each completed session that started
    // in the window, grouped server-side so the monthly insight can find records without loading
    // the sets. Same set rule as above: logged and not skipped, with a weight.
    @Query("""
            select new com.fitsync.workout.dto.SessionExerciseTopWeightRow(sess.sessionId, el.exercise.exerciseId, max(s.weightKg))
            from SessionEntry el
              join el.sets s
              join el.session sess
            where sess.userId = :userId
              and sess.status = :status
              and sess.startedAt between :from and :to
              and s.weightKg is not null
              and s.logged = true
              and s.status <> :skippedStatus
            group by sess.sessionId, el.exercise.exerciseId
            """)
    List<SessionExerciseTopWeightRow> findMaxSetWeightPerSessionExercise(@Param("userId") UUID userId,
                                                                         @Param("status") WorkoutSessionStatus status,
                                                                         @Param("skippedStatus") WorkoutSetStatus skippedStatus,
                                                                         @Param("from") Instant from,
                                                                         @Param("to") Instant to);
    // The progress pages read the sets of many sessions at once: every SET entry of the given
    // sessions of the user, with its exercise and its sets, in one query per chunk of ids.
    @Query("""
            select distinct el from SessionEntry el
              join fetch el.exercise
              left join fetch el.sets
            where el.session.userId = :userId
              and el.session.sessionId in :sessionIds
              and el.entryType = com.fitsync.workout.domain.EntryType.SET
            """)
    List<SessionEntry> findWithSetsBySessionIds(@Param("userId") UUID userId,
                                                            @Param("sessionIds") List<UUID> sessionIds);

    // Each exercise's best estimated single before a moment, as max(weight x (30 + reps)); the
    // service divides by 30. A session is placed by completion, else start, like the progress pages.
    // Only performed sets with a positive weight and reps count.
    @Query("""
            select new com.fitsync.workout.repository.ExerciseBestRow(
                       el.exercise.exerciseId, cast(max(s.weightKg * (30 + s.reps)) as BigDecimal))
            from SessionEntry el
              join el.sets s
              join el.session sess
            where sess.userId = :userId
              and sess.status = :status
              and coalesce(sess.completedAt, sess.startedAt) < :before
              and s.weightKg > 0
              and s.reps > 0
              and s.logged = true
              and s.status <> :skippedStatus
            group by el.exercise.exerciseId
            """)
    List<ExerciseBestRow> findBestE1rmBefore(@Param("userId") UUID userId,
                                             @Param("status") WorkoutSessionStatus status,
                                             @Param("skippedStatus") WorkoutSetStatus skippedStatus,
                                             @Param("before") Instant before);

    // Every log of one exercise in the user's completed sessions, with its session and sets, for the
    // exercise page. One query; bounded by how often one person did one exercise.
    @Query("""
            select distinct el from SessionEntry el
              join fetch el.session sess
              left join fetch el.sets
            where sess.userId = :userId
              and sess.status = :status
              and el.entryType = com.fitsync.workout.domain.EntryType.SET
              and el.exercise.exerciseId = :exerciseId
            """)
    List<SessionEntry> findExerciseHistoryWithSets(@Param("userId") UUID userId,
                                                               @Param("exerciseId") UUID exerciseId,
                                                               @Param("status") WorkoutSessionStatus status);
}
