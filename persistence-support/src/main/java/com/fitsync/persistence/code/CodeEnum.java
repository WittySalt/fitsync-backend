package com.fitsync.persistence.code;

import java.util.Arrays;

public interface CodeEnum {

    String getCode();

    String getDefaultDisplayName();

    String getDefaultDescription();

    default String[] getLegacyCodes() {
        return new String[0];
    }

    static <E extends Enum<E> & CodeEnum> E fromCode(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(enumType.getEnumConstants())
                .filter(candidate -> equalsIgnoreCase(candidate.getCode(), value)
                        || equalsIgnoreCase(candidate.name(), value)
                        || Arrays.stream(candidate.getLegacyCodes())
                        .anyMatch(alias -> equalsIgnoreCase(alias, value)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported " + enumType.getSimpleName() + " code: " + value));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return String.CASE_INSENSITIVE_ORDER.compare(left, right) == 0;
    }
}
