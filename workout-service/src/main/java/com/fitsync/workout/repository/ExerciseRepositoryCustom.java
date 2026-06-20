package com.fitsync.workout.repository;

import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExerciseRepositoryCustom {

    Page<ExerciseEntity> searchAccessible(UUID userId,
                                          ExerciseStatus status,
                                          String query,
                                          ExerciseType exerciseType,
                                          MuscleGroup primaryMuscleGroup,
                                          ExerciseEquipment equipment,
                                          WorkoutDifficulty difficulty,
                                          Pageable pageable);
}
