package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.MembershipStatus;
import com.fitsync.user.domain.MembershipTier;
import com.fitsync.user.domain.PrimaryGoal;

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
        String locationDisplay,
        String bio,
        MembershipTier membershipTier,
        MembershipStatus membershipStatus,
        boolean onboardingCompleted,
        PrimaryGoal primaryGoal) {
}
