package com.fitsync.workout.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.WorkoutSetStatus;

/**
 * One entry of a session as it is read back, so the timer, the completion page and History draw from
 * one list. A SET entry carries its exercise and its sets; a SEGMENT entry carries its kind, label,
 * planned and actual seconds, start and status, and no exercise.
 */
public record SessionEntryView(
        UUID entryId,
        EntryType entryType,
        Integer position,
        UUID exerciseId,
        String exerciseNameSnapshot,
        SegmentKind kind,
        String label,
        Integer plannedSeconds,
        Integer actualSeconds,
        Instant startedAt,
        WorkoutSetStatus status,
        LocalDate loggedOn,
        List<WorkoutExerciseSetLogView> sets) {

    public SessionEntryView {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    @Override
    public List<WorkoutExerciseSetLogView> sets() {
        return List.copyOf(sets);
    }
}
