package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fitsync.persistence.code.CodeEnum;

public enum LanguagePreference implements UserCodeEnum {
    EN("110001", "English", "English", "en"),
    ZH_CN("110002", "Simplified Chinese", "Simplified Chinese", "zh_cn");

    private final CodeEnumMetadata metadata;

    LanguagePreference(String code, String displayName, String description, String... legacyCodes) {
        this.metadata = new CodeEnumMetadata(code, displayName, description, legacyCodes);
    }

    @Override
    public CodeEnumMetadata metadata() {
        return metadata;
    }

    @JsonCreator
    public static LanguagePreference fromCode(String value) {
        return CodeEnum.fromCode(LanguagePreference.class, value);
    }
}
