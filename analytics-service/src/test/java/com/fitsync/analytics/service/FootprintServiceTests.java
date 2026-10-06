package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.fitsync.analytics.dto.AnalyticsFootprintResponse;
import com.fitsync.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

/** The Me page footprint on Tuesday 6 Oct 2026 in Singapore. */
class FootprintServiceTests {

    private final AchievementsServiceTests data = new AchievementsServiceTests();

    private FootprintService service() {
        return new FootprintService(data.snapshotRepository, data.workoutRepository, data.nutritionRepository,
                data.service("2026-10-06T04:00:00Z"), Clock.fixed(Instant.parse("2026-10-06T04:00:00Z"), ZoneOffset.UTC));
    }

    private static AnalyticsFootprintResponse.Day day(AnalyticsFootprintResponse response, String date) {
        return response.days().stream().filter(d -> d.date().equals(LocalDate.parse(date))).findFirst().orElseThrow();
    }

    @Test
    void theGridRunsFromTheMondayBeforeMemberSinceToTheSundayOfThisWeek() {
        data.trained("2026-08-04", 1);
        data.ate("2026-08-04", 3, "100", null);

        AnalyticsFootprintResponse response = service().getFootprint(AchievementsServiceTests.principal(),
                LocalDate.parse("2026-08-04"));

        assertThat(response.memberSince()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(response.from()).isEqualTo(LocalDate.parse("2026-08-03"));
        assertThat(response.to()).isEqualTo(LocalDate.parse("2026-10-11"));
        assertThat(response.days()).hasSize(70);
        assertThat(response.days().get(0).date()).isEqualTo(LocalDate.parse("2026-08-03"));
        assertThat(response.days().get(69).date()).isEqualTo(LocalDate.parse("2026-10-11"));
        assertThat(day(response, "2026-08-04").trained()).isTrue();
        assertThat(day(response, "2026-08-04").logged()).isTrue();
        assertThat(day(response, "2026-10-07").trained()).isFalse();
    }

    @Test
    void aFastDayCountsAsLoggedAndTheTotalsAreLifetime() {
        data.trained("2026-09-01", 2);
        data.trained("2026-09-03", 1);
        data.ate("2026-09-01", 3, "100", null);
        data.fasted("2026-09-02");
        data.ate("2026-09-04", 0, "0", null);

        AnalyticsFootprintResponse response = service().getFootprint(AchievementsServiceTests.principal(), null);

        assertThat(day(response, "2026-09-02").logged()).isTrue();
        assertThat(day(response, "2026-09-02").trained()).isFalse();
        // A rollup row without meals is not a logged day.
        assertThat(day(response, "2026-09-04").logged()).isFalse();
        assertThat(response.totals().workouts()).isEqualTo(3);
        assertThat(response.totals().daysLogged()).isEqualTo(2);
        // First workout and first meal logged.
        assertThat(response.totals().badges()).isEqualTo(2);
    }

    @Test
    void withoutMemberSinceTheGridStartsOnTheMondayOfTheFirstLoggedDay() {
        data.ate("2026-09-02", 1, "50", null);
        data.trained("2026-09-10", 1);

        AnalyticsFootprintResponse response = service().getFootprint(AchievementsServiceTests.principal(), null);

        assertThat(response.memberSince()).isNull();
        assertThat(response.from()).isEqualTo(LocalDate.parse("2026-08-31"));
    }

    @Test
    void anEmptyAccountGetsThisWeekOnly() {
        AnalyticsFootprintResponse response = service().getFootprint(AchievementsServiceTests.principal(), null);

        assertThat(response.from()).isEqualTo(LocalDate.parse("2026-10-05"));
        assertThat(response.days()).hasSize(7).allSatisfy(d -> {
            assertThat(d.trained()).isFalse();
            assertThat(d.logged()).isFalse();
        });
        assertThat(response.totals()).isEqualTo(new AnalyticsFootprintResponse.Totals(0, 0, 0));
    }

    @Test
    void aMemberSinceInTheFutureIsRejected() {
        assertThatThrownBy(() -> service().getFootprint(AchievementsServiceTests.principal(),
                LocalDate.parse("2026-10-07")))
                .isInstanceOf(BadRequestException.class);
    }
}
