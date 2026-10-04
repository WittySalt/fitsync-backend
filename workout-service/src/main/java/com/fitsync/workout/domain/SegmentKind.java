package com.fitsync.workout.domain;

/** The part a time segment plays in a cardio or sport day, with the label it gets when it has none. */
public enum SegmentKind {
    WARMUP("Warm-up"),
    STEADY("Steady"),
    WORK("Work"),
    EASY("Easy"),
    COOLDOWN("Cool-down");

    private final String label;

    SegmentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
