package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "exerciseId": "11111111-1111-1111-1111-111111111111",
          "sequence": 1,
          "performedSets": 3,
          "performedReps": 24,
          "totalVolumeKg": 1440.0,
          "distanceKm": null,
          "durationSeconds": null,
          "averageHeartRate": 132,
          "maxHeartRate": 158,
          "notes": "Last set was hard.",
          "sets": [
            {
              "setNumber": 1,
              "reps": 8,
              "weightKg": 55.0,
              "durationSeconds": null,
              "distanceKm": null,
              "restSeconds": 120,
              "perceivedExertion": 7,
              "notes": "Warm-up working set."
            }
          ]
        }
        """)
public record WorkoutExerciseLogInput(
        @NotNull UUID exerciseId,
        @NotNull @Min(1) Integer sequence,
        @Min(0) Integer performedSets,
        @Min(0) Integer performedReps,
        @DecimalMin("0.0") BigDecimal totalVolumeKg,
        @DecimalMin("0.0") BigDecimal distanceKm,
        @Min(0) Integer durationSeconds,
        @Min(0) @Max(260) Integer averageHeartRate,
        @Min(0) @Max(260) Integer maxHeartRate,
        @Size(max = 300) String notes,
        @Valid List<WorkoutExerciseSetLogInput> sets) {
}
