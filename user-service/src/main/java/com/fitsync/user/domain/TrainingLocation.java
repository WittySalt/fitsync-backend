package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum TrainingLocation implements CodeEnum {
    HOME("210001", "Home", "Home", "home"),
    GYM("210002", "Gym", "Gym", "gym"),
    OUTDOOR("210003", "Outdoor", "Outdoor", "outdoor"),
    MIXED("210004", "Mixed", "Mixed", "mixed");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    TrainingLocation(String code, String displayName, String description, String... legacyCodes) {
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
    public String[] getLegacyCodes() { return legacyCodes.clone(); }

    @JsonCreator
    public static TrainingLocation fromCode(String value) {
        return CodeEnum.fromCode(TrainingLocation.class, value);
    }
}
