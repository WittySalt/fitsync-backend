package com.fitsync.workout.dto;

import java.util.List;

/**
 * The sport group of the training page insight: sessions of {@link com.fitsync.workout.domain.SessionKind#SPORT}
 * in the described period, broken down by activity. There is no earlier-week comparison for this
 * group, since a sport-by-sport comparison is not part of this endpoint's contract.
 */
public record SportInsightView(int sessions, int minutes, List<SportActivityInsightView> activities) {

    public SportInsightView {
        activities = activities == null ? List.of() : List.copyOf(activities);
    }

    @Override
    public List<SportActivityInsightView> activities() {
        return List.copyOf(activities);
    }
}
