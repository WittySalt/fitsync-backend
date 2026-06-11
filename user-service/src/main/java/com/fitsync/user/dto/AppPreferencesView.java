package com.fitsync.user.dto;

import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.UnitSystem;

public record AppPreferencesView(
        UnitSystem preferredUnits,
        LanguagePreference language,
        String timezone,
        PrivacyLevel privacyLevel,
        boolean dataSharingConsent) {
}
