package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum PrivacyLevel implements UserCodeEnum {
    PRIVATE("120001", "Only me", "Only me", "private"),
    FRIENDS("120002", "Friends only", "Friends only", "friends"),
    PUBLIC("120003", "Public profile", "Public profile", "public");

    private final CodeEnumMetadata metadata;

    PrivacyLevel(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static PrivacyLevel fromCode(String value) {
        return CodeEnum.fromCode(PrivacyLevel.class, value);
    }
}
