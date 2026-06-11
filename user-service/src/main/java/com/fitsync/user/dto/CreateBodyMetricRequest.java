package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fitsync.user.domain.MetricSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "weightKg": 72.4,
          "bodyFatRate": 18.6,
          "waistCm": 82.0,
          "hipCm": 96.0,
          "chestCm": 98.0,
          "armCm": 32.0,
          "thighCm": 55.5,
          "calfCm": 37.0,
          "measuredAt": "2026-06-12T08:00:00Z",
          "source": "MANUAL",
          "note": "Morning weigh-in before breakfast"
        }
        """)
public record CreateBodyMetricRequest(
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal weightKg,
        @DecimalMin("0.0") @DecimalMax("100.0")
        BigDecimal bodyFatRate,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal waistCm,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal hipCm,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal chestCm,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal armCm,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal thighCm,
        @DecimalMin("1.0") @DecimalMax("500.0")
        BigDecimal calfCm,
        @NotNull Instant measuredAt,
        @NotNull MetricSource source,
        @Size(max = 200) String note) {
}
