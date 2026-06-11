package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum MacroPreference implements CodeEnum {
    BALANCED("320001", "Balanced", "Balanced macros", "balanced"),
    HIGH_PROTEIN("320002", "High protein", "Higher protein", "high_protein"),
    LOW_CARB("320003", "Low carbohydrate", "Lower carbohydrate", "low_carb");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    MacroPreference(String code, String displayName, String description, String... legacyCodes) {
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
    public String[] getLegacyCodes() { return legacyCodes; }

    @JsonCreator
    public static MacroPreference fromCode(String value) {
        return CodeEnum.fromCode(MacroPreference.class, value);
    }
}
