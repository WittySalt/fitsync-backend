package com.fitsync.user.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fitsync.persistence.code.CodeEnum;

public interface UserCodeEnum extends CodeEnum {

    CodeEnumMetadata metadata();

    @Override
    @JsonValue
    default String getCode() {
        return metadata().code();
    }

    @Override
    default String getDefaultDisplayName() {
        return metadata().displayName();
    }

    @Override
    default String getDefaultDescription() {
        return metadata().description();
    }

    @Override
    default String[] getLegacyCodes() {
        return metadata().legacyCodes();
    }
}
