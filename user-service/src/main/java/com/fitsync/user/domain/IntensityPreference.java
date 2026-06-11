package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum IntensityPreference implements CodeEnum {
    LOW("220001", "Low", "Low intensity", "low"),
    MODERATE("220002", "Moderate", "Moderate intensity", "moderate"),
    HIGH("220003", "High", "High intensity", "high");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    IntensityPreference(String code, String displayName, String description, String... legacyCodes) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.legacyCodes = legacyCodes;
    }

    @Override
    @JsonValue
    public String getCode() { return code; }

    @Override
    public String getDefaultDisplayName() { return displayName; }

    @Override
    public String getDefaultDescription() { return description; }

    @Override
    public String[] getLegacyCodes() { return legacyCodes; }

    @JsonCreator
    public static IntensityPreference fromCode(String value) {
        return CodeEnum.fromCode(IntensityPreference.class, value);
    }
}
