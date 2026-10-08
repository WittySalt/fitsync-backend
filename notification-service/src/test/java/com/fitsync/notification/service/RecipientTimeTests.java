package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class RecipientTimeTests {

    private static final LocalTime TEN_PM = LocalTime.of(22, 0);
    private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);

    @Test
    void quietHoursAcrossMidnight() {
        assertThat(RecipientTime.inQuietHours(LocalTime.of(23, 0), TEN_PM, SEVEN_AM)).isTrue();
        assertThat(RecipientTime.inQuietHours(LocalTime.of(6, 59), TEN_PM, SEVEN_AM)).isTrue();
        assertThat(RecipientTime.inQuietHours(SEVEN_AM, TEN_PM, SEVEN_AM)).isFalse();
        assertThat(RecipientTime.inQuietHours(LocalTime.NOON, TEN_PM, SEVEN_AM)).isFalse();
    }

    @Test
    void quietHoursWithinADayAndMissingOrEmptyWindows() {
        assertThat(RecipientTime.inQuietHours(LocalTime.of(13, 0), LocalTime.NOON, LocalTime.of(14, 0))).isTrue();
        assertThat(RecipientTime.inQuietHours(LocalTime.of(14, 0), LocalTime.NOON, LocalTime.of(14, 0))).isFalse();
        assertThat(RecipientTime.inQuietHours(LocalTime.NOON, null, SEVEN_AM)).isFalse();
        assertThat(RecipientTime.inQuietHours(LocalTime.NOON, LocalTime.NOON, LocalTime.NOON)).isFalse();
    }

    @Test
    void quietHoursEndIsTheNextTimeTheClockShowsIt() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime lateEvening = ZonedDateTime.of(2026, 10, 7, 23, 0, 0, 0, zone);
        ZonedDateTime earlyMorning = ZonedDateTime.of(2026, 10, 7, 5, 0, 0, 0, zone);

        assertThat(RecipientTime.quietHoursEnd(lateEvening, SEVEN_AM)).isEqualTo(Instant.parse("2026-10-08T11:00:00Z"));
        assertThat(RecipientTime.quietHoursEnd(earlyMorning, SEVEN_AM)).isEqualTo(Instant.parse("2026-10-07T11:00:00Z"));
    }

    @Test
    void missingOrUnknownTimezoneFallsBackToSingapore() {
        assertThat(RecipientTime.zoneOf((String) null)).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(RecipientTime.zoneOf(" ")).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(RecipientTime.zoneOf("Not/AZone")).isEqualTo(ZoneId.of("Asia/Singapore"));
        assertThat(RecipientTime.zoneOf("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
    }
}
