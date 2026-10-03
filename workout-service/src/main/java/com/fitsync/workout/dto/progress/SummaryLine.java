package com.fitsync.workout.dto.progress;

/**
 * One of the three lines on an activity or exercise summary. {@code text} is the whole line and
 * always starts with {@code figure}, the part a client may set in bold. Built from numbers by rules.
 */
public record SummaryLine(String figure, String text, Trend trend) {
}
