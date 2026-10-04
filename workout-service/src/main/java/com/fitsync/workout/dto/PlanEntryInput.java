package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * One entry of a plan day. A SET entry (the default) is a move: exerciseId with sets, reps, weight,
 * rest and tempo, and plannedSeconds for a time-based move. A SEGMENT entry is a stretch of time on a
 * cardio or sport day: kind, label, plannedSeconds and rounds for a repeated interval. The position
 * orders the entries of the day; without one, the order of the list is used.
 */
@Schema(example = """
        {
          "entryType": "SEGMENT",
          "kind": "STEADY",
          "label": "Steady",
          "position": 2,
          "plannedSeconds": 1200,
          "rounds": null
        }
        """)
public record PlanEntryInput(
        EntryType entryType,
        UUID exerciseId,
        SegmentKind kind,
        @Size(max = 40) String label,
        @Min(1) @Max(200) Integer position,
        @Min(0) @Max(50) Integer targetSets,
        @Min(0) @Max(1000) Integer targetReps,
        @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal targetWeightKg,
        @Min(0) @Max(3600) Integer restSeconds,
        @DecimalMin("1.0") @DecimalMax("10.0") @Digits(integer = 2, fraction = 1) BigDecimal tempoSeconds,
        @Min(1) @Max(36000) Integer plannedSeconds,
        @Min(1) @Max(50) Integer rounds,
        @Size(max = 300) String notes) {

    /** The type, SET when none was given. */
    public EntryType type() {
        return entryType == null ? EntryType.SET : entryType;
    }

    /** A move entry. */
    public static PlanEntryInput set(UUID exerciseId, Integer position, Integer targetSets, Integer targetReps,
                                     BigDecimal targetWeightKg, Integer restSeconds) {
        return new PlanEntryInput(EntryType.SET, exerciseId, null, null, position, targetSets, targetReps,
                targetWeightKg, restSeconds, null, null, null, null);
    }

    /** A time segment entry. */
    public static PlanEntryInput segment(SegmentKind kind, String label, Integer position, Integer plannedSeconds,
                                         Integer rounds) {
        return new PlanEntryInput(EntryType.SEGMENT, null, kind, label, position, null, null, null, null, null,
                plannedSeconds, rounds, null);
    }
}
