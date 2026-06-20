package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutExerciseLogRepository extends JpaRepository<WorkoutExerciseLogEntity, UUID> {

    @Query(value = """
            select new com.fitsync.workout.dto.ExerciseHistoryEntryView(
                session.sessionId,
                exerciseLog.exerciseLogId,
                setLog.setLogId,
                exercise.exerciseId,
                exerciseLog.exerciseNameSnapshot,
                session.name,
                session.startedAt,
                session.completedAt,
                exerciseLog.sequence,
                setLog.setNumber,
                setLog.reps,
                setLog.weightKg,
                setLog.durationSeconds,
                setLog.distanceKm,
                setLog.restSeconds,
                setLog.perceivedExertion,
                setLog.notes)
            from WorkoutExerciseSetLogEntity setLog
            join setLog.exerciseLog exerciseLog
            join exerciseLog.session session
            join exerciseLog.exercise exercise
            where session.userId = :userId
              and exercise.exerciseId = :exerciseId
              and session.status = com.fitsync.workout.domain.WorkoutSessionStatus.COMPLETED
              and setLog.logged = true
              and (:from is null or session.startedAt >= :from)
              and (:to is null or session.startedAt <= :to)
            order by session.startedAt desc, exerciseLog.sequence asc, setLog.setNumber asc
            """,
            countQuery = """
            select count(setLog)
            from WorkoutExerciseSetLogEntity setLog
            join setLog.exerciseLog exerciseLog
            join exerciseLog.session session
            join exerciseLog.exercise exercise
            where session.userId = :userId
              and exercise.exerciseId = :exerciseId
              and session.status = com.fitsync.workout.domain.WorkoutSessionStatus.COMPLETED
              and setLog.logged = true
              and (:from is null or session.startedAt >= :from)
              and (:to is null or session.startedAt <= :to)
            """)
    Page<ExerciseHistoryEntryView> findExerciseHistory(@Param("userId") UUID userId,
                                                       @Param("exerciseId") UUID exerciseId,
                                                       @Param("from") Instant from,
                                                       @Param("to") Instant to,
                                                       Pageable pageable);

    @Query("""
            select new com.fitsync.workout.dto.ExerciseRecordSetRow(
                session.sessionId,
                exerciseLog.exerciseLogId,
                exercise.exerciseId,
                exerciseLog.exerciseNameSnapshot,
                session.startedAt,
                session.completedAt,
                setLog.reps,
                setLog.weightKg)
            from WorkoutExerciseSetLogEntity setLog
            join setLog.exerciseLog exerciseLog
            join exerciseLog.session session
            join exerciseLog.exercise exercise
            where session.userId = :userId
              and exercise.exerciseId = :exerciseId
              and session.status = com.fitsync.workout.domain.WorkoutSessionStatus.COMPLETED
              and setLog.logged = true
              and (:excludedSessionId is null or session.sessionId <> :excludedSessionId)
            """)
    List<ExerciseRecordSetRow> findRecordRows(@Param("userId") UUID userId,
                                              @Param("exerciseId") UUID exerciseId,
                                              @Param("excludedSessionId") UUID excludedSessionId);
}
