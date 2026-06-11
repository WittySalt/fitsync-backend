package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum DietaryPreference implements CodeEnum {
    NONE("300001", "No dietary pattern", "No dietary pattern", "none"),
    HALAL("300002", "Halal", "Halal", "halal"),
    VEGETARIAN("300003", "Vegetarian", "Vegetarian", "vegetarian"),
    VEGAN("300004", "Vegan", "Vegan", "vegan"),
    KETO("300005", "Keto", "Keto", "keto"),
    LOW_CARB("300006", "Low carbohydrate", "Low carbohydrate", "low_carb");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    DietaryPreference(String code, String displayName, String description, String... legacyCodes) {
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
    public String[] getLegacyCodes() { return legacyCodes.clone(); }

    @JsonCreator
    public static DietaryPreference fromCode(String value) {
        return CodeEnum.fromCode(DietaryPreference.class, value);
    }
}
