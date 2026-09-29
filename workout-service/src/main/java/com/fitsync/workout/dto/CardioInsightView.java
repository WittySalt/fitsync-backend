package com.fitsync.workout.dto;

import java.math.BigDecimal;

/**
 * The cardio group of the training page insight: sessions of {@link com.fitsync.workout.domain.SessionKind#CARDIO}
 * in the described period, compared with the week before it.
 */
public record CardioInsightView(
        int sessions,
        int earlierSessions,
        BigDecimal distancePerSessionKm,
        BigDecimal earlierDistancePerSessionKm,
        Integer paceSecondsPerKm,
        Integer earlierPaceSecondsPerKm,
        Integer minutesPerSession,
        Integer earlierMinutesPerSession) {
}
