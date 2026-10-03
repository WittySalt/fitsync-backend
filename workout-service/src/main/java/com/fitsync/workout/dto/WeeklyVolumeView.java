package com.fitsync.workout.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code previousWeekKg} is the whole of last week. A comparison made part-way through this week has to
 * use {@code previousWeekSameDaysKg} instead: last week from Monday up to the same weekday as the
 * reference date (the same number of elapsed days in the user's zone), so the two sides cover the same
 * span. The fields are additive; the old ones keep their meaning.
 */
@Schema(example = """
        {
          "thisWeekKg": 3846.00,
          "previousWeekKg": 1716.00,
          "weekBeforePreviousKg": 0.00,
          "thisWeekSessionsWithVolume": 2,
          "previousWeekSessionsWithVolume": 1,
          "previousWeekSameDaysKg": 1716.00,
          "previousWeekSameDaysSessionsWithVolume": 1
        }
        """)
public record WeeklyVolumeView(
        BigDecimal thisWeekKg,
        BigDecimal previousWeekKg,
        BigDecimal weekBeforePreviousKg,
        int thisWeekSessionsWithVolume,
        int previousWeekSessionsWithVolume,
        BigDecimal previousWeekSameDaysKg,
        int previousWeekSameDaysSessionsWithVolume) {

    /** A view whose same-days figures equal last week's totals, for fixtures that predate them. */
    public WeeklyVolumeView(BigDecimal thisWeekKg,
                            BigDecimal previousWeekKg,
                            BigDecimal weekBeforePreviousKg,
                            int thisWeekSessionsWithVolume,
                            int previousWeekSessionsWithVolume) {
        this(thisWeekKg, previousWeekKg, weekBeforePreviousKg, thisWeekSessionsWithVolume,
                previousWeekSessionsWithVolume, previousWeekKg, previousWeekSessionsWithVolume);
    }
}
