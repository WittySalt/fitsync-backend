package com.fitsync.workout.dto;

import java.util.List;

import com.fitsync.workout.domain.MuscleGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "muscleGroup": "CORE",
          "sessions": 4,
          "exerciseNames": ["Plank Hold"]
        }
        """)
public record MostTrainedView(
        MuscleGroup muscleGroup,
        int sessions,
        List<String> exerciseNames) {

    public MostTrainedView {
        exerciseNames = exerciseNames == null ? List.of() : List.copyOf(exerciseNames);
    }

    @Override
    public List<String> exerciseNames() {
        return List.copyOf(exerciseNames);
    }
}
