package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "exerciseId": "11111111-1111-1111-1111-111111111111",
          "sequence": 1,
          "targetSets": 3,
          "targetReps": 8,
          "targetWeightKg": 60.0,
          "targetDurationSeconds": null,
          "targetDistanceKm": null,
          "restSeconds": 120,
          "notes": "Controlled tempo.",
          "tempoSeconds": 3.0,
          "setTargets": [
            {
              "setNumber": 1,
              "targetReps": 8,
              "targetWeightKg": 55.0,
              "targetDurationSeconds": null,
              "targetDistanceKm": null,
              "restSeconds": 120
            }
          ]
        }
        """)
public record WorkoutPlanExerciseInput(
        @NotNull UUID exerciseId,
        @NotNull @Min(1) Integer sequence,
        @Min(0) Integer targetSets,
        @Min(0) Integer targetReps,
        @DecimalMin("0.0") BigDecimal targetWeightKg,
        @Min(0) Integer targetDurationSeconds,
        @DecimalMin("0.0") BigDecimal targetDistanceKm,
        @Min(0) Integer restSeconds,
        @Size(max = 300) String notes,
        @Valid List<WorkoutPlanExerciseSetTargetInput> setTargets,
        /** Seconds one rep takes when the exercise screen counts along; null uses the default of its kind. */
        @DecimalMin("1.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1) BigDecimal tempoSeconds) {

    public WorkoutPlanExerciseInput {
        setTargets = setTargets == null ? List.of() : List.copyOf(setTargets);
    }

    /** An exercise without a tempo of its own. */
    public WorkoutPlanExerciseInput(UUID exerciseId, Integer sequence, Integer targetSets, Integer targetReps,
                                    BigDecimal targetWeightKg, Integer targetDurationSeconds, BigDecimal targetDistanceKm,
                                    Integer restSeconds, String notes, List<WorkoutPlanExerciseSetTargetInput> setTargets) {
        this(exerciseId, sequence, targetSets, targetReps, targetWeightKg, targetDurationSeconds, targetDistanceKm,
                restSeconds, notes, setTargets, null);
    }

    @Override
    public List<WorkoutPlanExerciseSetTargetInput> setTargets() {
        return List.copyOf(setTargets);
    }
}
