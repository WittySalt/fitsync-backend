package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum CalorieTrackingMode implements CodeEnum {
    OFF("310001", "Off", "Do not track calories", "off"),
    FLEXIBLE("310002", "Flexible", "Flexible calorie tracking", "flexible"),
    STRICT("310003", "Strict", "Strict calorie tracking", "strict");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    CalorieTrackingMode(String code, String displayName, String description, String... legacyCodes) {
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
    public static CalorieTrackingMode fromCode(String value) {
        return CodeEnum.fromCode(CalorieTrackingMode.class, value);
    }
}
