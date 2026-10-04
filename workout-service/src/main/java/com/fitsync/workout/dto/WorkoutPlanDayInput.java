package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.CardioLocation;
import com.fitsync.workout.domain.DayTargetKind;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
          "targetKind": null,
          "targetValue": null,
          "location": null,
          "effortTarget": null,
          "entries": []
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
        /** The older shape of a day's moves, kept for one release; each one is stored as a SET entry. */
        @Valid @Size(max = 30) List<WorkoutPlanExerciseInput> exercises,
        /** What a cardio day aims at, with targetValue in minutes, km or minutes a km. */
        DayTargetKind targetKind,
        @DecimalMin("0.0") @DecimalMax("99999.99") BigDecimal targetValue,
        CardioLocation location,
        /** The effort the person expects, 1 to 10 (easy 3, moderate 6, hard 8). */
        @Min(1) @Max(10) Integer effortTarget,
        /** The day's entries, moves or time segments; replaces exercises when given. */
        @Valid @Size(max = 60) List<PlanEntryInput> entries) {

    public WorkoutPlanDayInput {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** A day in the older shape, with its moves as exercises and no cardio fields. */
    public WorkoutPlanDayInput(UUID planDayId, Integer dayOfWeek, SessionKind kind, String title,
                               SportActivity sportActivity, Integer estimatedDurationMinutes,
                               List<WorkoutPlanExerciseInput> exercises) {
        this(planDayId, dayOfWeek, kind, title, sportActivity, estimatedDurationMinutes, exercises,
                null, null, null, null, List.of());
    }

    @Override
    public List<WorkoutPlanExerciseInput> exercises() {
        return List.copyOf(exercises);
    }

    @Override
    public List<PlanEntryInput> entries() {
        return List.copyOf(entries);
    }
}
