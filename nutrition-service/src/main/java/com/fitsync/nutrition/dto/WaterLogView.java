package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.UUID;

public record WaterLogView(
        UUID waterId,
        Integer amountMl,
        Instant loggedAt,
        String timezone) {
}
