package com.fitsync.analytics.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rule-based finding of a Progress page (contract section 0). {@code title} and {@code context} are fixed
 * sentences filled with the rule's figures, never model text; {@code series} feeds the small chart and
 * {@code inputs} holds the figures the rule compared, so the client and the coach can cite them.
 */
public record ProgressHighlight(String code, String title, List<Point> series, String unit, String context,
                                Map<String, Object> inputs) {

    public ProgressHighlight {
        series = series == null ? List.of() : List.copyOf(series);
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }

    @Override
    public List<Point> series() {
        return List.copyOf(series);
    }

    @Override
    public Map<String, Object> inputs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }

    /** One point of the small chart. */
    public record Point(String label, Double value) {
    }
}
