package com.fitsync.contracts.events.user;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The profile facts other services keep a copy of. gender and birthDate were added after the first version and
 * are null when the person has not given them or when an older producer wrote the event.
 */
public record UserProfileUpdatedPayload(
        String displayName,
        BigDecimal heightCm,
        String activityLevel,
        boolean onboardingCompleted,
        List<String> updatedFields,
        String timezone,
        String gender,
        LocalDate birthDate) {

    public UserProfileUpdatedPayload(String displayName,
                                     BigDecimal heightCm,
                                     String activityLevel,
                                     boolean onboardingCompleted,
                                     List<String> updatedFields,
                                     String timezone) {
        this(displayName, heightCm, activityLevel, onboardingCompleted, updatedFields, timezone, null, null);
    }

    public UserProfileUpdatedPayload(String displayName,
                                     BigDecimal heightCm,
                                     String activityLevel,
                                     boolean onboardingCompleted,
                                     List<String> updatedFields) {
        this(displayName, heightCm, activityLevel, onboardingCompleted, updatedFields, null, null, null);
    }

    public UserProfileUpdatedPayload {
        updatedFields = updatedFields == null ? List.of() : List.copyOf(updatedFields);
    }
}
