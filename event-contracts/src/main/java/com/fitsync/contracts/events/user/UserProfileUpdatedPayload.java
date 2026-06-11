package com.fitsync.contracts.events.user;

import java.math.BigDecimal;
import java.util.List;

public record UserProfileUpdatedPayload(
        String displayName,
        BigDecimal heightCm,
        String activityLevel,
        boolean onboardingCompleted,
        List<String> updatedFields) {

    public UserProfileUpdatedPayload {
        updatedFields = updatedFields == null ? List.of() : List.copyOf(updatedFields);
    }
}
