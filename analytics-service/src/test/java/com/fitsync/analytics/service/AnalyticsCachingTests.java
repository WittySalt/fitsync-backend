package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.dto.ComparePeriod;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsCachingTests {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AnalyticsCacheInvalidationService cacheInvalidationService;

    @MockBean
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @MockBean
    private AnalyticsUserGoalProgressRepository goalProgressRepository;

    @MockBean
    private AnalyticsUserDailyBodyRepository dailyBodyRepository;

    @MockBean
    private AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;

    @MockBean
    private AnalyticsUserDailyNutritionRepository dailyNutritionRepository;

    @MockBean
    private Clock clock;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldCacheDashboardAndEvictAfterInvalidation() {
        // getDashboard now re-checks the snapshot against the clock, so the clock must be fixed.
        setUserZoneClockInstant("2026-06-14T08:00:00Z");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setLatestWeightKg(new BigDecimal("78.40"));
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        when(dashboardSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of());

        var first = analyticsService.getDashboard(principal);
        var second = analyticsService.getDashboard(principal);

        assertThat(first.timezone()).isEqualTo("Asia/Singapore");
        assertThat(second.body().latestWeightKg()).isEqualTo(78.4);
        verify(goalProgressRepository, times(1))
                .findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE");

        cacheInvalidationService.evictForUser(userId);
        analyticsService.getDashboard(principal);

        verify(goalProgressRepository, times(2))
                .findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE");
    }

    @Test
    void shouldNotServeYesterdaysDashboardNutritionFromCacheAfterTheUsersMidnight() {
        // Today's calories are read from today's daily row, so a cached dashboard must not outlive the
        // user's day even when no event evicts it: the key carries the user's local date.
        UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "gina@example.com", "Gina", "USER");
        when(dashboardSnapshotRepository.findById(userId))
                .thenReturn(Optional.of(dashboardSnapshot(userId, "Asia/Singapore")));
        when(goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of());
        AnalyticsUserDailyNutritionEntity yesterday = new AnalyticsUserDailyNutritionEntity();
        yesterday.setUserId(userId);
        yesterday.setActivityDate(LocalDate.parse("2026-06-14"));
        yesterday.setTotalCaloriesConsumed(900);
        yesterday.setTotalProteinGrams(new BigDecimal("60.00"));
        when(dailyNutritionRepository.findByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-14")))
                .thenReturn(Optional.of(yesterday));

        // 15:30 UTC is 23:30 in Singapore on 14 June.
        setUserZoneClockInstant("2026-06-14T15:30:00Z");
        assertThat(analyticsService.getDashboard(principal).nutrition().todayCaloriesConsumed()).isEqualTo(900);

        // 16:30 UTC is 00:30 on 15 June in Singapore; nothing has been eaten yet.
        setUserZoneClockInstant("2026-06-14T16:30:00Z");
        assertThat(analyticsService.getDashboard(principal).nutrition().todayCaloriesConsumed()).isZero();
    }

    @Test
    void shouldEvictOnlyTargetUserDashboardCache() {
        // getDashboard now re-checks the snapshot against the clock, so the clock must be fixed.
        setUserZoneClockInstant("2026-06-14T08:00:00Z");
        UUID targetUserId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID otherUserId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        CurrentUserPrincipal targetPrincipal =
                new CurrentUserPrincipal(targetUserId, "alice@example.com", "Alice", "USER");
        CurrentUserPrincipal otherPrincipal =
                new CurrentUserPrincipal(otherUserId, "bob@example.com", "Bob", "USER");

        AnalyticsUserDashboardSnapshotEntity targetSnapshot = dashboardSnapshot(targetUserId, "Asia/Singapore");
        AnalyticsUserDashboardSnapshotEntity otherSnapshot = dashboardSnapshot(otherUserId, "UTC");
        when(dashboardSnapshotRepository.findById(targetUserId)).thenReturn(Optional.of(targetSnapshot));
        when(dashboardSnapshotRepository.findById(otherUserId)).thenReturn(Optional.of(otherSnapshot));
        when(goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(targetUserId, "ACTIVE"))
                .thenReturn(List.of());
        when(goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(otherUserId, "ACTIVE"))
                .thenReturn(List.of());

        analyticsService.getDashboard(targetPrincipal);
        analyticsService.getDashboard(otherPrincipal);
        analyticsService.getDashboard(targetPrincipal);
        analyticsService.getDashboard(otherPrincipal);

        cacheInvalidationService.evictForUser(targetUserId);
        analyticsService.getDashboard(targetPrincipal);
        analyticsService.getDashboard(otherPrincipal);

        verify(goalProgressRepository, times(2))
                .findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(targetUserId, "ACTIVE");
        verify(goalProgressRepository, times(1))
                .findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(otherUserId, "ACTIVE");
    }

    @Test
    void shouldCacheWorkoutSummaryByUserAndDateRange() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "bob@example.com", "Bob", "USER");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-14");

        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());
        when(dashboardSnapshotRepository.findById(userId)).thenReturn(Optional.empty());

        analyticsService.getWorkoutSummary(principal, from, to);
        analyticsService.getWorkoutSummary(principal, from, to);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);

        analyticsService.getWorkoutSummary(principal, from, LocalDate.parse("2026-06-15"));

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, LocalDate.parse("2026-06-15"));
    }

    @Test
    void shouldCacheWorkoutSeriesAndEvictAfterInvalidation() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "cara@example.com", "Cara", "USER");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-14");

        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());

        analyticsService.getWorkoutSeries(principal, from, to, BodyTrendBucket.WEEK);
        analyticsService.getWorkoutSeries(principal, from, to, BodyTrendBucket.WEEK);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);

        cacheInvalidationService.evictForUser(userId);
        analyticsService.getWorkoutSeries(principal, from, to, BodyTrendBucket.WEEK);

        verify(dailyWorkoutRepository, times(2))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
    }

    @Test
    void shouldCacheActivityCalendarAndEvictAfterInvalidation() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "cara@example.com", "Cara", "USER");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-14");

        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());

        analyticsService.getActivityCalendar(principal, from, to);
        analyticsService.getActivityCalendar(principal, from, to);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);

        cacheInvalidationService.evictForUser(userId);
        analyticsService.getActivityCalendar(principal, from, to);

        verify(dailyWorkoutRepository, times(2))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
    }

    @Test
    void shouldCacheNutritionSeriesAndEvictAfterInvalidation() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "dina@example.com", "Dina", "USER");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-30");

        when(dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());

        analyticsService.getNutritionSeries(principal, from, to, BodyTrendBucket.MONTH);
        analyticsService.getNutritionSeries(principal, from, to, BodyTrendBucket.MONTH);

        verify(dailyNutritionRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);

        cacheInvalidationService.evictForUser(userId);
        analyticsService.getNutritionSeries(principal, from, to, BodyTrendBucket.MONTH);

        verify(dailyNutritionRepository, times(2))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
    }

    @Test
    void shouldCacheSummaryCompareByUserPeriodAndTodayAndEvictAfterInvalidation() {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "erin@example.com", "Erin", "USER");
        setClockDate("2026-06-18");
        LocalDate currentFrom = LocalDate.parse("2026-06-15");
        LocalDate currentTo = LocalDate.parse("2026-06-18");
        LocalDate previousFrom = LocalDate.parse("2026-06-08");
        LocalDate previousTo = LocalDate.parse("2026-06-11");
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, currentTo))
                .thenReturn(List.of());
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, previousFrom, previousTo))
                .thenReturn(List.of());
        when(dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, currentTo))
                .thenReturn(List.of());
        when(dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, previousFrom, previousTo))
                .thenReturn(List.of());

        analyticsService.getSummaryCompare(principal, ComparePeriod.WEEK);
        analyticsService.getSummaryCompare(principal, ComparePeriod.WEEK);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, currentTo);
        verify(dailyNutritionRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, previousFrom, previousTo);

        cacheInvalidationService.evictForUser(userId);
        analyticsService.getSummaryCompare(principal, ComparePeriod.WEEK);

        verify(dailyWorkoutRepository, times(2))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, currentTo);

        setClockDate("2026-06-19");
        LocalDate nextCurrentTo = LocalDate.parse("2026-06-19");
        LocalDate nextPreviousTo = LocalDate.parse("2026-06-12");
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, nextCurrentTo))
                .thenReturn(List.of());
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, previousFrom, nextPreviousTo))
                .thenReturn(List.of());
        when(dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, nextCurrentTo))
                .thenReturn(List.of());
        when(dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, previousFrom, nextPreviousTo))
                .thenReturn(List.of());

        analyticsService.getSummaryCompare(principal, ComparePeriod.WEEK);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, currentFrom, nextCurrentTo);
    }

    @Test
    void shouldCacheWeeklyReportByUserTimezoneWeekAcrossUtcMidnight() {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "fred@example.com", "Fred", "USER");
        when(dashboardSnapshotRepository.findById(userId))
                .thenReturn(Optional.of(dashboardSnapshot(userId, "Asia/Singapore")));
        LocalDate weekStart = LocalDate.parse("2026-07-13");
        LocalDate weekEnd = LocalDate.parse("2026-07-19");

        // Sunday 17:30 UTC is already Monday 01:30 in Singapore, so the default week is 2026-07-13.
        setUserZoneClockInstant("2026-07-12T17:30:00Z");
        var first = analyticsService.getWeeklyReport(principal, null);
        assertThat(first.from()).isEqualTo(weekStart);

        // The UTC date has rolled over but the Singapore week has not: same cache entry must serve.
        setUserZoneClockInstant("2026-07-13T00:30:00Z");
        var second = analyticsService.getWeeklyReport(principal, null);
        assertThat(second.from()).isEqualTo(weekStart);

        verify(dailyWorkoutRepository, times(1))
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, weekStart, weekEnd);
    }

    private void setUserZoneClockInstant(String instant) {
        org.mockito.Mockito.doAnswer(invocation ->
                        Clock.fixed(Instant.parse(instant), invocation.getArgument(0)))
                .when(clock).withZone(org.mockito.ArgumentMatchers.any());
    }

    private void setClockDate(String date) {
        setUserZoneClockInstant(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toString());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private AnalyticsUserDashboardSnapshotEntity dashboardSnapshot(UUID userId, String timezone) {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone(timezone);
        snapshot.setLatestWeightKg(new BigDecimal("78.40"));
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));
        return snapshot;
    }
}
