package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;

/** One completed or in-progress session shown on a day of the weekly insight. */
public record DaySessionView(
        UUID sessionId,
        SessionKind kind,
        String label,
        SportActivity sportActivity,
        WorkoutSessionStatus status,
        Integer minutes,
        BigDecimal distanceKm,
        UUID planDayId) {
}
