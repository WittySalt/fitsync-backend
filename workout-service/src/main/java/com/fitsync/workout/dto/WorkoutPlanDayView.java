package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.CardioLocation;
import com.fitsync.workout.domain.DayTargetKind;
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
        /** The day's moves in the older shape: its SET entries only. */
        List<WorkoutPlanExerciseView> exercises,
        DayTargetKind targetKind,
        BigDecimal targetValue,
        CardioLocation location,
        Integer effortTarget,
        /** Every entry of the day, moves and time segments, by position. */
        List<PlanEntryView> entries) {

    public WorkoutPlanDayView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** A day view in the older shape, without the cardio fields and entries. */
    public WorkoutPlanDayView(UUID planDayId, Integer dayOfWeek, SessionKind kind, String title,
                              SportActivity sportActivity, String sportLabel, Integer estimatedDurationMinutes,
                              List<WorkoutPlanExerciseView> exercises) {
        this(planDayId, dayOfWeek, kind, title, sportActivity, sportLabel, estimatedDurationMinutes, exercises,
                null, null, null, null, List.of());
    }

    @Override
    public List<WorkoutPlanExerciseView> exercises() {
        return List.copyOf(exercises);
    }

    @Override
    public List<PlanEntryView> entries() {
        return List.copyOf(entries);
    }
}
