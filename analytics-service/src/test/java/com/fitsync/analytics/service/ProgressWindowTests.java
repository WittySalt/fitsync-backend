package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.fitsync.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

class ProgressWindowTests {

    @Test
    void previousIsThePeriodOfTheSameLengthRightBefore() {
        ProgressWindow window = ProgressWindow.of(LocalDate.parse("2026-09-04"), LocalDate.parse("2026-10-03"));

        assertThat(window.days()).isEqualTo(30);
        assertThat(window.previousFrom()).isEqualTo(LocalDate.parse("2026-08-05"));
        assertThat(window.previousTo()).isEqualTo(LocalDate.parse("2026-09-03"));
        assertThat(window.contains(LocalDate.parse("2026-09-04"))).isTrue();
        assertThat(window.contains(LocalDate.parse("2026-09-03"))).isFalse();
        assertThat(window.containsPrevious(LocalDate.parse("2026-09-03"))).isTrue();
    }

    @Test
    void oneDayPeriodHasTheDayBeforeAsPrevious() {
        ProgressWindow window = ProgressWindow.of(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-03"));

        assertThat(window.previousFrom()).isEqualTo(LocalDate.parse("2026-10-02"));
        assertThat(window.previousTo()).isEqualTo(LocalDate.parse("2026-10-02"));
    }

    @Test
    void weeksRunMondayToSundayAcrossTheEdges() {
        // Sunday 6 Sept to Monday 14 Sept touches three Monday weeks.
        ProgressWindow window = ProgressWindow.of(LocalDate.parse("2026-09-06"), LocalDate.parse("2026-09-14"));

        assertThat(window.weekStarts()).containsExactly(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-07"),
                LocalDate.parse("2026-09-14"));
        assertThat(ProgressWindow.weekStart(LocalDate.parse("2026-09-13"))).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(ProgressWindow.weekStart(LocalDate.parse("2026-09-14"))).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(window.previousWeekStarts()).containsExactly(LocalDate.parse("2026-08-24"),
                LocalDate.parse("2026-08-31"));
    }

    @Test
    void rejectsFromAfterToAndPeriodsOver370Days() {
        assertThatThrownBy(() -> ProgressWindow.of(LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-03")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("from must not be after to");
        assertThatThrownBy(() -> ProgressWindow.of(LocalDate.parse("2025-09-28"), LocalDate.parse("2026-10-03")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("370");
        assertThatThrownBy(() -> ProgressWindow.of(null, LocalDate.parse("2026-10-03")))
                .isInstanceOf(BadRequestException.class);
        assertThat(ProgressWindow.of(LocalDate.parse("2025-09-29"), LocalDate.parse("2026-10-03")).days())
                .isEqualTo(370);
    }
}
