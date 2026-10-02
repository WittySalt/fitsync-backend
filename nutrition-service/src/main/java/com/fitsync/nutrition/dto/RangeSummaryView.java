package com.fitsync.nutrition.dto;

import java.time.LocalDate;
import java.util.List;

/** The days from {@code from} to {@code to}, both included, oldest first. */
public record RangeSummaryView(LocalDate from, LocalDate to, List<RangeDayView> days) {

    public RangeSummaryView {
        days = days == null ? List.of() : List.copyOf(days);
    }

    @Override
    public List<RangeDayView> days() {
        return List.copyOf(days);
    }
}
