package com.fitsync.contracts.events.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoalUpdatedPayload(
        UUID goalId,
        String goalType,
        BigDecimal targetValue,
        String targetUnit,
        LocalDate targetDate,
        String status) {
}
