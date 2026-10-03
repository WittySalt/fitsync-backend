package com.fitsync.web.timezone;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientZoneHeaderTests {

    @Test
    void parsesRegionIds() {
        assertThat(ClientZoneHeader.parse("Asia/Singapore")).contains(ZoneId.of("Asia/Singapore"));
        assertThat(ClientZoneHeader.parse(" America/New_York ")).contains(ZoneId.of("America/New_York"));
        assertThat(ClientZoneHeader.parse("UTC")).contains(ZoneId.of("UTC"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "+08:00", "Z", "UTC+8", "GMT+08:00", "asia/singapore", "Mars/Olympus",
            "Asia/Singapore; drop table", "../../etc/passwd"})
    void rejectsEverythingElse(String value) {
        assertThat(ClientZoneHeader.parse(value)).isEmpty();
    }

    @Test
    void rejectsNullAndOverLongValues() {
        assertThat(ClientZoneHeader.parse(null)).isEmpty();
        assertThat(ClientZoneHeader.parse("Asia/" + "x".repeat(ClientZoneHeader.MAX_LENGTH))).isEmpty();
    }
}
