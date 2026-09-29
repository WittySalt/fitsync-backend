package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "exerciseName": "Dumbbell Bench Press",
          "fromKg": 20.00,
          "fromDate": "2026-09-16",
          "toKg": 22.50,
          "toDate": "2026-09-25"
        }
        """)
public record LiftProgressView(
        String exerciseName,
        BigDecimal fromKg,
        LocalDate fromDate,
        BigDecimal toKg,
        LocalDate toDate) {
}
