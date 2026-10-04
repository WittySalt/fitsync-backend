package com.fitsync.workout.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The profile a default week is built from, as the client already holds it, so the workout service
 * asks no other service. Codes are the profile's own (BEGINNER, DUMBBELLS, CARDIO and so on, in either
 * case); a code it does not know is ignored.
 */
@Schema(example = """
        {
          "fitnessLevel": "BEGINNER",
          "weeklyWorkoutDays": 4,
          "preferredDurationMinutes": 45,
          "equipment": ["DUMBBELLS", "BANDS"],
          "focusAreas": ["FULL_BODY", "CARDIO"]
        }
        """)
public record PlanProposalRequest(
        @Size(max = 20) @Pattern(regexp = "[A-Za-z_]*") String fitnessLevel,
        @Min(1) @Max(7) Integer weeklyWorkoutDays,
        @Min(10) @Max(240) Integer preferredDurationMinutes,
        @Size(max = 12) List<@Size(max = 30) @Pattern(regexp = "[A-Za-z_]*") String> equipment,
        @Size(max = 8) List<@Size(max = 30) @Pattern(regexp = "[A-Za-z_]*") String> focusAreas) {

    public PlanProposalRequest {
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
    }

    @Override
    public List<String> equipment() {
        return List.copyOf(equipment);
    }

    @Override
    public List<String> focusAreas() {
        return List.copyOf(focusAreas);
    }
}
