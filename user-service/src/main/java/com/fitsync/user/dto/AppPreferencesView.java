package com.fitsync.user.dto;

import java.time.Instant;

import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.UnitSystem;

public record AppPreferencesView(
        UnitSystem preferredUnits,
        LanguagePreference language,
        String timezone,
        PrivacyLevel privacyLevel,
        boolean dataSharingConsent,
        // Set by the server when consent turns on, cleared when it turns off; never read from a request.
        Instant healthDataConsentAt) {
}
