package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum CalorieTrackingMode implements UserCodeEnum {
    OFF("310001", "Off", "Do not track calories", "off"),
    FLEXIBLE("310002", "Flexible", "Flexible calorie tracking", "flexible"),
    STRICT("310003", "Strict", "Strict calorie tracking", "strict");

    private final CodeEnumMetadata metadata;

    CalorieTrackingMode(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static CalorieTrackingMode fromCode(String value) {
        return CodeEnum.fromCode(CalorieTrackingMode.class, value);
    }
}
