package com.fitsync.workout.dto;

import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;

/** A plan day from earlier this week that is still not trained, shown when today itself has no
 * training so the missed days from this week are not lost from view. */
public record CarriedOverView(
        UUID planDayId,
        int dayOfWeek,
        SessionKind kind,
        String title,
        Integer estimatedMinutes) {
}
