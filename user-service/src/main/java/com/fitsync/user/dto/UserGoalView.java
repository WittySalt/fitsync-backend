package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;

public record UserGoalView(
        UUID goalId,
        GoalType goalType,
        BigDecimal targetValue,
        String targetUnit,
        LocalDate targetDate,
        GoalStatus status,
        String notes) {
}
