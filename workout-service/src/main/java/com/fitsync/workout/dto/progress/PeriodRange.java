package com.fitsync.workout.dto.progress;

import java.time.LocalDate;

/** An inclusive range of local dates in the caller's zone. */
public record PeriodRange(LocalDate from, LocalDate to) {
}
