package com.fitsync.workout.dto;

import com.fitsync.workout.domain.SportActivity;

/** One sport activity's share of the period's sport sessions. */
public record SportActivityInsightView(SportActivity activity, String label, int sessions, int minutes) {
}
