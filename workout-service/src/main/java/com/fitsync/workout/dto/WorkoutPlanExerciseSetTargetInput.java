package com.fitsync.workout.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "setNumber": 1,
          "targetReps": 8,
          "targetWeightKg": 55.0,
          "targetDurationSeconds": null,
          "targetDistanceKm": null,
          "restSeconds": 120
        }
        """)
public record WorkoutPlanExerciseSetTargetInput(
        @NotNull @Min(1) Integer setNumber,
        @Min(0) Integer targetReps,
        @DecimalMin("0.0") BigDecimal targetWeightKg,
        @Min(0) Integer targetDurationSeconds,
        @DecimalMin("0.0") BigDecimal targetDistanceKm,
        @Min(0) Integer restSeconds) {
}
