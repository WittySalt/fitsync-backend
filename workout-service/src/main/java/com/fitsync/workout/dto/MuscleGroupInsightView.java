package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.workout.domain.MuscleGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "muscleGroup": "CHEST",
          "sessionsThisWeek": 2,
          "inActivePlan": true,
          "trainedYesterday": false,
          "lastTrainedOn": "2026-09-25",
          "consecutiveSessions": 0,
          "exerciseNames": ["Dumbbell Bench Press"]
        }
        """)
public record MuscleGroupInsightView(
        MuscleGroup muscleGroup,
        int sessionsThisWeek,
        boolean inActivePlan,
        boolean trainedYesterday,
        LocalDate lastTrainedOn,
        int consecutiveSessions,
        List<String> exerciseNames) {

    public MuscleGroupInsightView {
        exerciseNames = exerciseNames == null ? List.of() : List.copyOf(exerciseNames);
    }

    @Override
    public List<String> exerciseNames() {
        return List.copyOf(exerciseNames);
    }
}
