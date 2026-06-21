package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum UnitSystem implements UserCodeEnum {
    METRIC("100001", "Metric", "Metric units", "metric"),
    IMPERIAL("100002", "Imperial", "Imperial units", "imperial");

    private final CodeEnumMetadata metadata;

    UnitSystem(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static UnitSystem fromCode(String value) {
        return CodeEnum.fromCode(UnitSystem.class, value);
    }
}
