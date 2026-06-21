package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum TrainingLocation implements UserCodeEnum {
    HOME("210001", "Home", "Home", "home"),
    GYM("210002", "Gym", "Gym", "gym"),
    OUTDOOR("210003", "Outdoor", "Outdoor", "outdoor"),
    MIXED("210004", "Mixed", "Mixed", "mixed");

    private final CodeEnumMetadata metadata;

    TrainingLocation(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static TrainingLocation fromCode(String value) {
        return CodeEnum.fromCode(TrainingLocation.class, value);
    }
}
