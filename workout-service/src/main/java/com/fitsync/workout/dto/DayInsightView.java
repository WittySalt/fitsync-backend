package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;

/** One of the seven days (Monday to Sunday) of the week shown by the weekly training insight. */
public record DayInsightView(
        LocalDate date,
        int dayOfWeek,
        DayKind kind,
        String label,
        DaySource source,
        Integer minutes,
        List<DaySessionView> sessions,
        PlannedDayView planned) {

    public DayInsightView {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public List<DaySessionView> sessions() {
        return List.copyOf(sessions);
    }
}
