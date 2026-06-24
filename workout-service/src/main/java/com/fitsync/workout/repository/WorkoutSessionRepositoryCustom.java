package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkoutSessionRepositoryCustom {

    Page<UUID> findHistoryIds(UUID userId,
                              Instant from,
                              Instant to,
                              WorkoutSessionStatus status,
                              WorkoutType workoutType,
                              Integer minDurationMinutes,
                              Integer maxDurationMinutes,
                              UUID planId,
                              Pageable pageable);
}
