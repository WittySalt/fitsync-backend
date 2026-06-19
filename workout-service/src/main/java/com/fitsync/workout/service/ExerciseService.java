package com.fitsync.workout.service;

import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.response.PageResponse;

public interface ExerciseService {

    ExerciseView createCustomExercise(CurrentUserPrincipal currentUser, CreateExerciseRequest request);

    ExerciseView getExercise(CurrentUserPrincipal currentUser, UUID exerciseId);

    ExerciseView updateCustomExercise(CurrentUserPrincipal currentUser, UUID exerciseId, UpdateExerciseRequest request);

    void archiveCustomExercise(CurrentUserPrincipal currentUser, UUID exerciseId);

    PageResponse<ExerciseView> listExercises(CurrentUserPrincipal currentUser,
                                             String query,
                                             ExerciseType exerciseType,
                                             MuscleGroup primaryMuscleGroup,
                                             ExerciseEquipment equipment,
                                             WorkoutDifficulty difficulty,
                                             int page,
                                             int size);
}
