package com.fitsync.user.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserGoal(
        UUID goalId,
        UUID userId,
        GoalType goalType,
        BigDecimal targetValue,
        String targetUnit,
        LocalDate targetDate,
        GoalStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
