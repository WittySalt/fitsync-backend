package com.fitsync.workout.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "sessions": [
            {
              "externalId": "apple-health-2026-06-12-001",
              "source": "DEVICE",
              "planId": null,
              "name": "Imported Strength Session",
              "workoutType": "STRENGTH",
              "status": "COMPLETED",
              "startedAt": "2026-06-12T10:00:00Z",
              "completedAt": "2026-06-12T11:05:00Z",
              "durationMinutes": 62,
              "caloriesBurned": 420,
              "perceivedExertion": 8,
              "notes": "Imported from device.",
              "exercises": []
            }
          ]
        }
        """)
public record ImportWorkoutSessionsRequest(
        @NotNull @Size(max = 100) List<ImportWorkoutSessionRowRequest> sessions) {

    public ImportWorkoutSessionsRequest {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public List<ImportWorkoutSessionRowRequest> sessions() {
        return List.copyOf(sessions);
    }
}
