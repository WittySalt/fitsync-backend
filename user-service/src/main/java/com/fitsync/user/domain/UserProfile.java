package com.fitsync.user.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfile(
        UUID userId,
        String displayName,
        String firstName,
        String lastName,
        String avatarUrl,
        LocalDate birthDate,
        Gender gender,
        BigDecimal heightCm,
        ActivityLevel activityLevel,
        String timezone,
        String bio,
        boolean onboardingCompleted,
        Instant createdAt,
        Instant updatedAt) {
}
