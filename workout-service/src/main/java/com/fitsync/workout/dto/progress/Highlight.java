package com.fitsync.workout.dto.progress;

import java.util.List;

/**
 * A rule-based finding: its code, a one-sentence title, the series for its small chart, the unit
 * of that series and one line of context. Never free text from a model.
 */
public record Highlight(String code, String title, List<ChartPoint> series, String unit, String context) {

    public Highlight {
        series = series == null ? List.of() : List.copyOf(series);
    }

    @Override
    public List<ChartPoint> series() {
        return List.copyOf(series);
    }
}
