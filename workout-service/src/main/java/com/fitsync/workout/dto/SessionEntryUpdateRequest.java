package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fitsync.workout.domain.WorkoutSetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * What one entry of a workout in progress is given. A SEGMENT entry takes actualSeconds, startedAt and
 * status (DONE or SKIPPED). A SET entry takes the set fields for the set named by setNumber, or for its
 * first pending set when none is named.
 */
@Schema(example = """
        {
          "actualSeconds": 1560,
          "startedAt": "2026-10-04T07:05:00Z",
          "status": "DONE"
        }
        """)
public record SessionEntryUpdateRequest(
        @Min(0) @Max(86400) Integer actualSeconds,
        Instant startedAt,
        WorkoutSetStatus status,
        @Min(1) @Max(100) Integer setNumber,
        @Min(0) Integer reps,
        @DecimalMin("0.0") BigDecimal weightKg,
        @Min(0) Integer durationSeconds,
        @DecimalMin("0.0") BigDecimal distanceKm,
        @Min(0) Integer restSeconds,
        @Min(1) @Max(10) Integer perceivedExertion,
        @Size(max = 300) String notes) {

    /** A segment update. */
    public static SessionEntryUpdateRequest segment(Integer actualSeconds, Instant startedAt, WorkoutSetStatus status) {
        return new SessionEntryUpdateRequest(actualSeconds, startedAt, status, null, null, null, null, null, null,
                null, null);
    }
}
