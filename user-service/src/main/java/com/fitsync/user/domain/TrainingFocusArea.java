package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum TrainingFocusArea implements CodeEnum {
    FULL_BODY("240001", "Full body", "Balanced full body training", "full_body"),
    UPPER("240002", "Upper body", "Upper body emphasis", "upper"),
    CORE("240003", "Core", "Core strength and stability", "core"),
    LOWER("240004", "Lower body", "Lower body emphasis", "lower"),
    MOBILITY("240005", "Mobility", "Mobility and movement quality", "mobility"),
    CARDIO("240006", "Cardio", "Cardiovascular conditioning", "cardio");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    TrainingFocusArea(String code, String displayName, String description, String... legacyCodes) {
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
    public static TrainingFocusArea fromCode(String value) {
        return CodeEnum.fromCode(TrainingFocusArea.class, value);
    }
}
