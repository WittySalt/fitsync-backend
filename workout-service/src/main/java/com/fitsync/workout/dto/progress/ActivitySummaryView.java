package com.fitsync.workout.dto.progress;

import java.util.List;

/**
 * The summary lines of one activity, as the weekly insight carries them for the Progress card, with
 * the weekday it is usually done on (1 Monday to 7 Sunday) and how many weeks in a row it has been
 * done on that weekday; both are empty when it has no such run.
 */
public record ActivitySummaryView(String key, String name, List<SummaryLine> summary, Integer usualWeekday,
                                  Integer streakWeeks) {

    public ActivitySummaryView {
        summary = summary == null ? List.of() : List.copyOf(summary);
    }

    /** A summary without a usual weekday. */
    public ActivitySummaryView(String key, String name, List<SummaryLine> summary) {
        this(key, name, summary, null, null);
    }

    @Override
    public List<SummaryLine> summary() {
        return List.copyOf(summary);
    }
}
