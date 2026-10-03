package com.fitsync.analytics.dto;

import java.time.LocalDate;

/** A period of a Progress page, both dates included. */
public record ProgressPeriod(LocalDate from, LocalDate to) {
}
