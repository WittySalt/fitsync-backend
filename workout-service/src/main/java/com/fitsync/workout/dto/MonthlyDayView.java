package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;

/** One local date of the month with at least one completed session. */
public record MonthlyDayView(
        LocalDate date,
        SessionKind kind,
        String label,
        int sessions,
        int minutes,
        List<MuscleGroup> muscles,
        boolean record) {

    public MonthlyDayView {
        muscles = muscles == null ? List.of() : List.copyOf(muscles);
    }

    @Override
    public List<MuscleGroup> muscles() {
        return List.copyOf(muscles);
    }
}
