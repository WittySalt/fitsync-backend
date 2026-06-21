package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum IntensityPreference implements UserCodeEnum {
    LOW("220001", "Low", "Low intensity", "low"),
    MODERATE("220002", "Moderate", "Moderate intensity", "moderate"),
    HIGH("220003", "High", "High intensity", "high");

    private final CodeEnumMetadata metadata;

    IntensityPreference(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static IntensityPreference fromCode(String value) {
        return CodeEnum.fromCode(IntensityPreference.class, value);
    }
}
