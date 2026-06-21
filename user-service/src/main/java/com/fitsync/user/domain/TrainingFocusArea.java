package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum TrainingFocusArea implements UserCodeEnum {
    FULL_BODY("240001", "Full body", "Balanced full body training", "full_body"),
    UPPER("240002", "Upper body", "Upper body emphasis", "upper"),
    CORE("240003", "Core", "Core strength and stability", "core"),
    LOWER("240004", "Lower body", "Lower body emphasis", "lower"),
    MOBILITY("240005", "Mobility", "Mobility and movement quality", "mobility"),
    CARDIO("240006", "Cardio", "Cardiovascular conditioning", "cardio");

    private final CodeEnumMetadata metadata;

    TrainingFocusArea(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static TrainingFocusArea fromCode(String value) {
        return CodeEnum.fromCode(TrainingFocusArea.class, value);
    }
}
