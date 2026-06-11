package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum UnitSystem implements CodeEnum {
    METRIC("100001", "Metric", "Metric units", "metric"),
    IMPERIAL("100002", "Imperial", "Imperial units", "imperial");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    UnitSystem(String code, String displayName, String description, String... legacyCodes) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.legacyCodes = legacyCodes;
    }

    @Override
    @JsonValue
    public String getCode() {
        return code;
    }

    @Override
    public String getDefaultDisplayName() { return displayName; }

    @Override
    public String getDefaultDescription() { return description; }

    @Override
    public String[] getLegacyCodes() { return legacyCodes; }

    @JsonCreator
    public static UnitSystem fromCode(String value) {
        return CodeEnum.fromCode(UnitSystem.class, value);
    }
}
