package com.fitsync.user.domain;

import java.util.Arrays;
import java.util.List;

public final class CodeEnumMetadata {

    private final String code;
    private final String displayName;
    private final String description;
    private final List<String> legacyCodes;

    public CodeEnumMetadata(String code, String displayName, String description, String... legacyCodes) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.legacyCodes = legacyCodes == null ? List.of() : List.copyOf(Arrays.asList(legacyCodes));
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String[] legacyCodes() {
        return legacyCodes.toArray(String[]::new);
    }
}
