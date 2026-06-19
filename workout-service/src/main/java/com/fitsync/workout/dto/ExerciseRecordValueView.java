package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "value": 125.0,
          "sessionId": "33333333-3333-3333-3333-333333333333",
          "achievedAt": "2026-06-12T11:05:00Z"
        }
        """)
public record ExerciseRecordValueView(
        BigDecimal value,
        UUID sessionId,
        Instant achievedAt) {
}
