package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutExerciseLogRepository extends JpaRepository<WorkoutExerciseLogEntity, UUID>,
        WorkoutExerciseLogRepositoryCustom {
}
