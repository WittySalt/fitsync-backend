package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum FitnessLevel implements UserCodeEnum {
    BEGINNER("200001", "Beginner", "Beginner", "beginner"),
    INTERMEDIATE("200002", "Intermediate", "Intermediate", "intermediate"),
    ADVANCED("200003", "Advanced", "Advanced", "advanced");

    private final CodeEnumMetadata metadata;

    FitnessLevel(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static FitnessLevel fromCode(String value) {
        return CodeEnum.fromCode(FitnessLevel.class, value);
    }
}
