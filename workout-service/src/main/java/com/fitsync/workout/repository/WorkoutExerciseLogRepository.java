package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.dto.ExerciseTopWeightRow;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutExerciseLogRepository extends JpaRepository<WorkoutExerciseLogEntity, UUID>,
        WorkoutExerciseLogRepositoryCustom {

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
            from WorkoutExerciseLogEntity el
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
}
