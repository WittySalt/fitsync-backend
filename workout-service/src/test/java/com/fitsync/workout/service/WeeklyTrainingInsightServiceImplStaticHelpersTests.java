package com.fitsync.workout.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit coverage for {@link WeeklyTrainingInsightServiceImpl}'s pure date-arithmetic helpers. These
 * run with no Spring context, verifying the week/timezone math independently of persistence.
 */
class WeeklyTrainingInsightServiceImplStaticHelpersTests {

    @Test
    void mondayOfReturnsSameDateWhenAlreadyMonday() {
        // 2026-09-21 is a Monday.
        assertEquals(LocalDate.parse("2026-09-21"), WeeklyTrainingInsightServiceImpl.mondayOf(LocalDate.parse("2026-09-21")));
    }

    @Test
    void mondayOfReturnsPrecedingMondayForMidWeekDate() {
        // 2026-09-25 is a Friday in the same week as 2026-09-21.
        assertEquals(LocalDate.parse("2026-09-21"), WeeklyTrainingInsightServiceImpl.mondayOf(LocalDate.parse("2026-09-25")));
    }

    @Test
    void mondayOfReturnsPrecedingMondayForSunday() {
        // 2026-09-27 is the Sunday that ends the week starting 2026-09-21.
        assertEquals(LocalDate.parse("2026-09-21"), WeeklyTrainingInsightServiceImpl.mondayOf(LocalDate.parse("2026-09-27")));
    }

    @Test
    void mondayOfRollsBackAcrossAMonthBoundary() {
        // 2026-09-28 is the Monday starting the next week; 2026-10-01 (Thursday) falls in it.
        assertEquals(LocalDate.parse("2026-09-28"), WeeklyTrainingInsightServiceImpl.mondayOf(LocalDate.parse("2026-10-01")));
    }

    @Test
    void resolveZoneDefaultsToUtcForNull() {
        assertEquals(ZoneOffset.UTC, WeeklyTrainingInsightServiceImpl.resolveZone(null));
    }

    @Test
    void resolveZoneDefaultsToUtcForBlank() {
        assertEquals(ZoneOffset.UTC, WeeklyTrainingInsightServiceImpl.resolveZone("   "));
    }

    @Test
    void resolveZoneParsesAValidIanaZoneId() {
        assertEquals(ZoneId.of("Asia/Singapore"), WeeklyTrainingInsightServiceImpl.resolveZone("Asia/Singapore"));
    }

    @Test
    void resolveZoneRejectsAnUnknownZoneId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WeeklyTrainingInsightServiceImpl.resolveZone("Not/AZone"));
        assertEquals("The timezone is not a recognised zone id.", ex.getMessage());
    }
}
