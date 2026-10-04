package com.fitsync.workout.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A plan that is still being built, for its coverage: its week of days in the same shape the plan
 * create request takes, and the two profile figures the minutes note compares against.
 */
@Schema(example = """
        {
          "days": [
            {"dayOfWeek": 1, "kind": "STRENGTH", "title": "Full body A", "estimatedDurationMinutes": 45,
             "entries": [{"entryType": "SET", "exerciseId": "11111111-1111-1111-1111-111111111111",
                          "position": 1, "targetSets": 3, "targetReps": 10}]}
          ],
          "weeklyWorkoutDays": 4,
          "preferredDurationMinutes": 45
        }
        """)
public record PlanDraftRequest(
        @Valid @Size(max = 7) List<WorkoutPlanDayInput> days,
        @Min(1) @Max(7) Integer weeklyWorkoutDays,
        @Min(5) @Max(600) Integer preferredDurationMinutes) {

    public PlanDraftRequest {
        days = days == null ? List.of() : List.copyOf(days);
    }

    @Override
    public List<WorkoutPlanDayInput> days() {
        return List.copyOf(days);
    }

    /** The profile's minutes a week, or null when either figure is missing. */
    public Integer profileMinutesPerWeek() {
        return weeklyWorkoutDays == null || preferredDurationMinutes == null
                ? null
                : weeklyWorkoutDays * preferredDurationMinutes;
    }
}
