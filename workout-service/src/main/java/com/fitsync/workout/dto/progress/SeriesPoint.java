package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One dated value of a series; {@code isRecord} marks a new estimated best single. */
public record SeriesPoint(LocalDate date, BigDecimal value, @JsonProperty("isRecord") boolean isRecord) {
}
