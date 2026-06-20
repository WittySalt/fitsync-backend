package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.messaging.AnalyticsEventPublisher;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Weekly scheduled job that emits a {@link WeeklyRecapGeneratedPayload} per active user summarising
 * the prior ISO week. Users with no activity that week are skipped. Emission is idempotent per
 * user/week via the publisher's deterministic event id, so re-runs do not duplicate recaps.
 */
@Service
public class AnalyticsWeeklyRecapService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWeeklyRecapService.class);
    private static final int BATCH_SIZE = 200;

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final Clock clock;

    public AnalyticsWeeklyRecapService(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                       AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                       AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                       AnalyticsEventPublisher analyticsEventPublisher,
                                       Clock clock) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.clock = clock;
    }

    @Scheduled(cron = "${fitsync.analytics.weekly-recap.cron}", zone = "UTC")
    public void runWeeklyRecap() {
        runWeeklyRecapOnce();
    }

    RecapSummary runWeeklyRecapOnce() {
        long startedAt = System.nanoTime();
        LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate weekStart = todayUtc.with(WeekFields.ISO.dayOfWeek(), 1).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);

        int emitted = 0;
        int skipped = 0;
        int failed = 0;
        int page = 0;
        Slice<UUID> userIds;
        do {
            userIds = dashboardSnapshotRepository.findSnapshotUserIds(PageRequest.of(page, BATCH_SIZE));
            for (UUID userId : userIds.getContent()) {
                try {
                    if (emitWeeklyRecap(userId, weekStart, weekEnd)) {
                        emitted++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("Analytics weekly recap failed userId={} exceptionType={} exceptionMessage={}",
                            sanitizeForLog(userId),
                            sanitizeForLog(exception.getClass().getName()),
                            sanitizeForLog(exception.getMessage()));
                }
            }
            page++;
        } while (userIds.hasNext());

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Analytics weekly recap completed weekStart={} emitted={} skipped={} failed={} elapsedMs={}",
                weekStart, emitted, skipped, failed, elapsedMs);
        return new RecapSummary(emitted, skipped, failed, elapsedMs);
    }

    private boolean emitWeeklyRecap(UUID userId, LocalDate weekStart, LocalDate weekEnd) {
        List<AnalyticsUserDailyWorkoutEntity> workouts = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, weekStart, weekEnd);
        List<AnalyticsUserDailyNutritionEntity> nutrition = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, weekStart, weekEnd);

        int workoutCount = workouts.stream().mapToInt(row -> defaultInt(row.getCompletedWorkoutCount())).sum();
        int workoutMinutes = workouts.stream().mapToInt(row -> defaultInt(row.getTotalWorkoutMinutes())).sum();
        int caloriesBurned = workouts.stream().mapToInt(row -> defaultInt(row.getTotalCaloriesBurned())).sum();
        int caloriesConsumed = nutrition.stream().mapToInt(row -> defaultInt(row.getTotalCaloriesConsumed())).sum();

        Set<LocalDate> activeDates = new HashSet<>();
        workouts.forEach(row -> activeDates.add(row.getActivityDate()));
        nutrition.forEach(row -> activeDates.add(row.getActivityDate()));

        if (workoutCount == 0 && caloriesConsumed == 0 && activeDates.isEmpty()) {
            return false;
        }

        analyticsEventPublisher.publishWeeklyRecap(userId, new WeeklyRecapGeneratedPayload(
                weekStart, weekEnd, workoutCount, workoutMinutes, caloriesBurned, caloriesConsumed, activeDates.size()));
        return true;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }

    record RecapSummary(int emitted, int skipped, int failed, long elapsedMs) {
    }
}
