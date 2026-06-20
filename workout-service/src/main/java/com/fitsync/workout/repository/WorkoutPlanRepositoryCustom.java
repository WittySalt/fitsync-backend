package com.fitsync.workout.repository;

import java.util.UUID;

import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkoutPlanRepositoryCustom {

    Page<WorkoutPlanEntity> findByUserIdAndOptionalStatus(UUID userId,
                                                          WorkoutPlanStatus status,
                                                          Pageable pageable);
}
