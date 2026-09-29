package com.fitsync.workout.dto;

/**
 * The mobility group of the training page insight: sessions of {@link com.fitsync.workout.domain.SessionKind#MOBILITY}
 * in the described period, compared with the week before it.
 */
public record MobilityInsightView(int sessions, int minutes, int earlierSessions, int earlierMinutes) {
}
