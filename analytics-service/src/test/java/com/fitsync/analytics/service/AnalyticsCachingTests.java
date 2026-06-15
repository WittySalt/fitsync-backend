package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
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

    @Test
    void shouldCacheDashboardAndEvictAfterInvalidation() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setLatestWeightKg(new BigDecimal("78.40"));
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        when(dashboardSnapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)).thenReturn(List.of());

        var first = analyticsService.getDashboard(principal);
        var second = analyticsService.getDashboard(principal);

        assertThat(first.timezone()).isEqualTo("Asia/Singapore");
        assertThat(second.body().latestWeightKg()).isEqualTo(78.4);
        verify(dashboardSnapshotRepository, times(1)).findById(userId);
        verify(goalProgressRepository, times(1)).findByUserIdOrderByTargetDateAscGoalIdAsc(userId);

        cacheInvalidationService.evictAllForUser(userId);
        analyticsService.getDashboard(principal);

        verify(dashboardSnapshotRepository, times(2)).findById(userId);
        verify(goalProgressRepository, times(2)).findByUserIdOrderByTargetDateAscGoalIdAsc(userId);
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
}
