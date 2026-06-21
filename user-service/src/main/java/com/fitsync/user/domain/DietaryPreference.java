package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum DietaryPreference implements UserCodeEnum {
    NONE("300001", "No dietary pattern", "No dietary pattern", "none"),
    HALAL("300002", "Halal", "Halal", "halal"),
    VEGETARIAN("300003", "Vegetarian", "Vegetarian", "vegetarian"),
    VEGAN("300004", "Vegan", "Vegan", "vegan"),
    KETO("300005", "Keto", "Keto", "keto"),
    LOW_CARB("300006", "Low carbohydrate", "Low carbohydrate", "low_carb");

    private final CodeEnumMetadata metadata;

    DietaryPreference(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static DietaryPreference fromCode(String value) {
        return CodeEnum.fromCode(DietaryPreference.class, value);
    }
}
