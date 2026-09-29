package com.fitsync.workout.dto;

import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

/** The active plan's schedule for a given weekday, and whether this week's occurrence of it has
 * already been trained, wherever in the week that happened. */
public record PlannedDayView(
        UUID planDayId,
        SessionKind kind,
        String title,
        SportActivity sportActivity,
        Integer estimatedMinutes,
        Integer moves,
        boolean fulfilled) {
}
