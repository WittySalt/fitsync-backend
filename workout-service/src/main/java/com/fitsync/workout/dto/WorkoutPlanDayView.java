package com.fitsync.workout.dto;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

public record WorkoutPlanDayView(
        UUID planDayId,
        Integer dayOfWeek,
        SessionKind kind,
        String title,
        SportActivity sportActivity,
        String sportLabel,
        Integer estimatedDurationMinutes,
        List<WorkoutPlanExerciseView> exercises) {

    public WorkoutPlanDayView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanExerciseView> exercises() {
        return List.copyOf(exercises);
    }
}
