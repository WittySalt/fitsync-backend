package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum LanguagePreference implements CodeEnum {
    EN("110001", "English", "English", "en"),
    ZH_CN("110002", "Simplified Chinese", "Simplified Chinese", "zh_cn");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    LanguagePreference(String code, String displayName, String description, String... legacyCodes) {
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
    public static LanguagePreference fromCode(String value) {
        return CodeEnum.fromCode(LanguagePreference.class, value);
    }
}
