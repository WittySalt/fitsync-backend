package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "exerciseName": "Dumbbell Bench Press",
          "weightKg": 22.50,
          "achievedOn": "2026-09-21",
          "previousBestKg": 20.00
        }
        """)
public record TrainingRecordView(
        String exerciseName,
        BigDecimal weightKg,
        LocalDate achievedOn,
        BigDecimal previousBestKg) {
}
