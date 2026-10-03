package com.fitsync.web.timezone;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the {@code X-Timezone} header, the single place the client's zone travels.
 *
 * <p>Accepted values are IANA region ids that the JVM knows by exact spelling, such as
 * {@code Asia/Singapore} or {@code UTC}. Fixed offsets ({@code +08:00}, {@code Z},
 * {@code UTC+8}) are rejected because an offset has no daylight-saving rules and would give a
 * wrong "today" for part of the year. A blank, over-long or unknown value is treated as absent.
 */
public final class ClientZoneHeader {

    public static final String NAME = "X-Timezone";
    public static final int MAX_LENGTH = 64;

    private static final Set<String> KNOWN_IDS = ZoneId.getAvailableZoneIds();

    private ClientZoneHeader() {
    }

    public static Optional<ZoneId> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH || !KNOWN_IDS.contains(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(value));
        } catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
}
