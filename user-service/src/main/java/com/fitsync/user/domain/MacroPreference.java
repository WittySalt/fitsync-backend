package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum MacroPreference implements UserCodeEnum {
    BALANCED("320001", "Balanced", "Balanced macros", "balanced"),
    HIGH_PROTEIN("320002", "High protein", "Higher protein", "high_protein"),
    LOW_CARB("320003", "Low carbohydrate", "Lower carbohydrate", "low_carb");

    private final CodeEnumMetadata metadata;

    MacroPreference(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static MacroPreference fromCode(String value) {
        return CodeEnum.fromCode(MacroPreference.class, value);
    }
}
