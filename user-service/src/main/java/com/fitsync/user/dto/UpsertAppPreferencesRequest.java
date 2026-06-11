package com.fitsync.user.dto;

import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.UnitSystem;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "App preference enum fields are serialized as stable code values.",
        example = """
        {
          "preferredUnits": "100001",
          "language": "110001",
          "timezone": "Asia/Singapore",
          "privacyLevel": "120002",
          "dataSharingConsent": true
        }
        """)
public record UpsertAppPreferencesRequest(
        @NotNull UnitSystem preferredUnits,
        @NotNull LanguagePreference language,
        @Size(max = 80) String timezone,
        @NotNull PrivacyLevel privacyLevel,
        boolean dataSharingConsent) {
}
