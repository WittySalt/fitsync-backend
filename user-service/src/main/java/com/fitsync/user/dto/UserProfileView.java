package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;

public record UserProfileView(
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
        boolean onboardingCompleted) {
}
