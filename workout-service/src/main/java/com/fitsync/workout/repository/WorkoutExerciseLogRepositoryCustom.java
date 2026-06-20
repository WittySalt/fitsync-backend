package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkoutExerciseLogRepositoryCustom {

    Page<ExerciseHistoryEntryView> findExerciseHistory(UUID userId,
                                                       UUID exerciseId,
                                                       Instant from,
                                                       Instant to,
                                                       Pageable pageable);

    List<ExerciseRecordSetRow> findRecordRows(UUID userId,
                                              UUID exerciseId,
                                              UUID excludedSessionId);
}
