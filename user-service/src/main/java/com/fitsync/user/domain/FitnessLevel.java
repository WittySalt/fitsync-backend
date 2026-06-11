package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum FitnessLevel implements CodeEnum {
    BEGINNER("200001", "Beginner", "Beginner", "beginner"),
    INTERMEDIATE("200002", "Intermediate", "Intermediate", "intermediate"),
    ADVANCED("200003", "Advanced", "Advanced", "advanced");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    FitnessLevel(String code, String displayName, String description, String... legacyCodes) {
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
    public static FitnessLevel fromCode(String value) {
        return CodeEnum.fromCode(FitnessLevel.class, value);
    }
}
