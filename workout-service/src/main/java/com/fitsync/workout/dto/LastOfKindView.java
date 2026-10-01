package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The most recent past session of today's training kind: when it happened, and, when it carried
 * a distance, the pace that came from it. {@code sessionId} names that session and {@code
 * exerciseNames} lists its exercises in order (at most six, empty for a session without any). */
public record LastOfKindView(
        LocalDate date,
        Integer minutes,
        BigDecimal distanceKm,
        Integer paceSecondsPerKm,
        UUID sessionId,
        List<String> exerciseNames) {

    public LastOfKindView {
        exerciseNames = exerciseNames == null ? List.of() : List.copyOf(exerciseNames);
    }
}
