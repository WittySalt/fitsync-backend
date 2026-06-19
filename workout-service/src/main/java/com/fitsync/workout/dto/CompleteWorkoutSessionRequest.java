package com.fitsync.workout.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "completedAt": "2026-06-12T11:05:00Z"
        }
        """)
public record CompleteWorkoutSessionRequest(
        Instant completedAt) {
}
