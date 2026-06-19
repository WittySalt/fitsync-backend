package com.fitsync.workout.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "reps": 8,
          "weightKg": 55.0,
          "durationSeconds": null,
          "distanceKm": null,
          "restSeconds": 120,
          "perceivedExertion": 7,
          "notes": "Warm-up working set."
        }
        """)
public record UpsertWorkoutExerciseSetRequest(
        @Min(0) Integer reps,
        @DecimalMin("0.0") BigDecimal weightKg,
        @Min(0) Integer durationSeconds,
        @DecimalMin("0.0") BigDecimal distanceKm,
        @Min(0) Integer restSeconds,
        @Min(1) @Max(10) Integer perceivedExertion,
        @Size(max = 300) String notes) {
}
