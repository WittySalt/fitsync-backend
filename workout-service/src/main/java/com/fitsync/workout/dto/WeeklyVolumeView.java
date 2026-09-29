package com.fitsync.workout.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "thisWeekKg": 3846.00,
          "previousWeekKg": 1716.00,
          "weekBeforePreviousKg": 0.00,
          "thisWeekSessionsWithVolume": 2,
          "previousWeekSessionsWithVolume": 1
        }
        """)
public record WeeklyVolumeView(
        BigDecimal thisWeekKg,
        BigDecimal previousWeekKg,
        BigDecimal weekBeforePreviousKg,
        int thisWeekSessionsWithVolume,
        int previousWeekSessionsWithVolume) {
}
