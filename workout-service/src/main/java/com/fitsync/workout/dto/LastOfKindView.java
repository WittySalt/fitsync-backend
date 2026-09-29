package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The most recent past session of today's training kind: when it happened, and, when it carried
 * a distance, the pace that came from it. */
public record LastOfKindView(
        LocalDate date,
        Integer minutes,
        BigDecimal distanceKm,
        Integer paceSecondsPerKm) {
}
