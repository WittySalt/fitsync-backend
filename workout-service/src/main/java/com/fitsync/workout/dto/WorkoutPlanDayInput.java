package com.fitsync.workout.dto;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "dayOfWeek": 1,
          "kind": "STRENGTH",
          "title": "Upper Push",
          "sportActivity": null,
          "estimatedDurationMinutes": 60,
          "exercises": []
        }
        """)
public record WorkoutPlanDayInput(
        /** Ignored on create; matched by dayOfWeek on update so a retained day keeps its id. */
        UUID planDayId,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull SessionKind kind,
        @NotBlank @Size(max = 120) String title,
        SportActivity sportActivity,
        @Min(1) @Max(600) Integer estimatedDurationMinutes,
        @Valid @Size(max = 30) List<WorkoutPlanExerciseInput> exercises) {

    public WorkoutPlanDayInput {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanExerciseInput> exercises() {
        return List.copyOf(exercises);
    }
}
