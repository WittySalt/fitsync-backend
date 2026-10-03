package com.fitsync.workout.dto.progress;

import java.util.List;

/** The summary lines of one activity, as the weekly insight carries them for the Progress card. */
public record ActivitySummaryView(String key, String name, List<SummaryLine> summary) {

    public ActivitySummaryView {
        summary = summary == null ? List.of() : List.copyOf(summary);
    }

    @Override
    public List<SummaryLine> summary() {
        return List.copyOf(summary);
    }
}
