package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fitsync.user.domain.GoalStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "targetValue": 68.0,
          "targetUnit": "kg",
          "targetDate": "2026-09-30",
          "status": "ACTIVE",
          "notes": "Cut slowly while maintaining strength."
        }
        """)
public record UpsertUserGoalRequest(
        @DecimalMin("0.0")
        BigDecimal targetValue,
        @Size(max = 20) String targetUnit,
        LocalDate targetDate,
        @NotNull GoalStatus status,
        @Size(max = 300) String notes) {
}
