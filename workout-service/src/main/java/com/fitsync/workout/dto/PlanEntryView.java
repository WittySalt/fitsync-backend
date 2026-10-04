package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;

/**
 * One entry of a plan day as it is read back. A SET entry carries its exercise and targets; a SEGMENT
 * entry carries its kind, label, planned seconds and rounds, and no exercise.
 */
public record PlanEntryView(
        UUID entryId,
        EntryType entryType,
        Integer position,
        UUID exerciseId,
        String exerciseNameSnapshot,
        SegmentKind kind,
        String label,
        Integer plannedSeconds,
        Integer rounds,
        Integer targetSets,
        Integer targetReps,
        BigDecimal targetWeightKg,
        Integer targetDurationSeconds,
        BigDecimal targetDistanceKm,
        Integer restSeconds,
        BigDecimal tempoSeconds,
        String notes,
        String primaryImageUrl) {
}
