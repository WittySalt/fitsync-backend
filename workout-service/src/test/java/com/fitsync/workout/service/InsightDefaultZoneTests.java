package com.fitsync.workout.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.workout.entity.UserTimezoneEntity;
import com.fitsync.workout.repository.UserTimezoneRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * H-6: an insight request without a timezone is computed in the zone kept in the person's profile,
 * not in UTC. The clock is Sunday 27 September 20:00 UTC, which is already Monday 28 September
 * 04:00 in Singapore, so the two zones disagree about today and about the week.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
@Import(InsightDefaultZoneTests.FixedClockConfig.class)
class InsightDefaultZoneTests {

    @Autowired
    private WeeklyTrainingInsightService weeklyService;

    @Autowired
    private TrainingPageInsightService trainingPageService;

    @Autowired
    private MonthlyTrainingInsightService monthlyService;

    @Autowired
    private UserTimezoneRepository userTimezoneRepository;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("31111111-1111-1111-1111-111111111111"), "zed@example.com", "Zed", "USER");

    @BeforeEach
    void clearZones() {
        userTimezoneRepository.deleteAll();
    }

    @Test
    void withoutAStoredZoneTheDefaultIsUtc() {
        var weekly = weeklyService.getWeeklyTrainingInsight(user, null, null);

        assertEquals("UTC", weekly.timezone());
        assertEquals(LocalDate.parse("2026-09-27"), weekly.referenceDate());
        assertEquals(LocalDate.parse("2026-09-21"), weekly.weekStart());
    }

    @Test
    void weeklyTrainingAndMonthlyInsightsDefaultToTheStoredProfileZone() {
        storeZone("Asia/Singapore");

        var weekly = weeklyService.getWeeklyTrainingInsight(user, null, null);
        assertEquals("Asia/Singapore", weekly.timezone());
        assertEquals(LocalDate.parse("2026-09-28"), weekly.referenceDate());
        assertEquals(LocalDate.parse("2026-09-28"), weekly.weekStart());

        var training = trainingPageService.getTrainingPageInsight(user, "", null);
        assertEquals("Asia/Singapore", training.timezone());
        assertEquals(LocalDate.parse("2026-09-28"), training.referenceDate());

        var monthly = monthlyService.getMonthlyTrainingInsight(user, null, null);
        assertEquals("Asia/Singapore", monthly.timezone());
        assertEquals("2026-09", monthly.month());
    }

    @Test
    void anExplicitZoneStillWinsOverTheStoredOne() {
        storeZone("Asia/Singapore");

        var weekly = weeklyService.getWeeklyTrainingInsight(user, "UTC", null);

        assertEquals("UTC", weekly.timezone());
        assertEquals(LocalDate.parse("2026-09-27"), weekly.referenceDate());
    }

    @Test
    void anotherUsersStoredZoneIsNotUsed() {
        userTimezoneRepository.save(new UserTimezoneEntity(
                UUID.fromString("32222222-2222-2222-2222-222222222222"), "Asia/Singapore",
                Instant.parse("2026-09-01T00:00:00Z")));

        var weekly = weeklyService.getWeeklyTrainingInsight(user, null, null);

        assertEquals("UTC", weekly.timezone());
    }

    @Test
    void aStoredZoneThatCannotBeUsedFallsBackToUtcButABadRequestZoneIsRejected() {
        userTimezoneRepository.save(new UserTimezoneEntity(
                user.userId(), "Not/AZone", Instant.parse("2026-09-01T00:00:00Z")));

        assertEquals("UTC", weeklyService.getWeeklyTrainingInsight(user, null, null).timezone());
        assertThrows(IllegalArgumentException.class,
                () -> weeklyService.getWeeklyTrainingInsight(user, "Not/AZone", null));
    }

    private void storeZone(String zone) {
        userTimezoneRepository.save(new UserTimezoneEntity(user.userId(), zone, Instant.parse("2026-09-01T00:00:00Z")));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock insightDefaultZoneTestClock() {
            return Clock.fixed(Instant.parse("2026-09-27T20:00:00Z"), ZoneOffset.UTC);
        }
    }
}
