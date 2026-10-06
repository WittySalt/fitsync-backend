package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.MembershipStatus;
import com.fitsync.user.domain.MembershipTier;
import com.fitsync.user.domain.PrimaryGoal;

/**
 * The profile block of GET /api/v1/users/me.
 *
 * @param createdAt when the account's profile was first written, which the Me page shows as "Member since"
 */
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
        PrimaryGoal primaryGoal,
        Instant createdAt) {
}
