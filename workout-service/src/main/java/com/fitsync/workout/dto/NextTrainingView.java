package com.fitsync.workout.dto;

import java.time.LocalDate;

import com.fitsync.workout.domain.DayKind;

/** The first upcoming day, within the next week, that is scheduled or habitually trained. */
public record NextTrainingView(
        LocalDate date,
        DayKind kind,
        String label,
        String title,
        Integer estimatedMinutes) {
}
