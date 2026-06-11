package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public enum PrivacyLevel implements CodeEnum {
    PRIVATE("120001", "Only me", "Only me", "private"),
    FRIENDS("120002", "Friends only", "Friends only", "friends"),
    PUBLIC("120003", "Public profile", "Public profile", "public");

    private final String code;
    private final String displayName;
    private final String description;
    private final String[] legacyCodes;

    PrivacyLevel(String code, String displayName, String description, String... legacyCodes) {
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
    public static PrivacyLevel fromCode(String value) {
        return CodeEnum.fromCode(PrivacyLevel.class, value);
    }
}
