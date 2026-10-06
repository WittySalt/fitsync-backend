package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsWeeklyRecapMarkEntity;
import com.fitsync.analytics.messaging.AnalyticsEventPublisher;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsWeeklyRecapMarkRepository;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Weekly scheduled job that emits a {@link WeeklyRecapGeneratedPayload} per active user summarising
 * the prior ISO week. Users with no activity that week are skipped.
 *
 * <p>The same run also happens once on startup. The cron alone has no catch-up, so a stack that was
 * off at Monday 00:15 UTC left every user's last recap a week older than the last full week, and the
 * coach quoted it as "last week". Each emitted week is recorded in {@code analytics_weekly_recap_marks}
 * and a user who already has a mark for the week is skipped, so the recap for a week is emitted once
 * however often the service restarts (the publisher's deterministic event id stays as a second guard).
 */
@Service
public class AnalyticsWeeklyRecapService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsWeeklyRecapService.class);
    private static final int BATCH_SIZE = 200;

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final AnalyticsWeeklyRecapMarkRepository recapMarkRepository;
    private final Clock clock;
    private final boolean catchUpOnStartup;

    public AnalyticsWeeklyRecapService(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                       AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                       AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                       AnalyticsEventPublisher analyticsEventPublisher,
                                       AnalyticsWeeklyRecapMarkRepository recapMarkRepository,
                                       Clock clock,
                                       @Value("${fitsync.analytics.weekly-recap.catch-up-on-startup:true}")
                                       boolean catchUpOnStartup) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.recapMarkRepository = recapMarkRepository;
        this.clock = clock;
        this.catchUpOnStartup = catchUpOnStartup;
    }

    /**
     * Catches up a missed Monday: every user without a recap for the last full week gets it now.
     * Users already marked for that week are skipped, so a restart on any other day emits nothing new.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (!catchUpOnStartup) {
            return;
        }
        try {
            runWeeklyRecapOnce();
        } catch (RuntimeException exception) {
            // A failed catch-up must not stop the service; the Monday cron runs regardless.
            log.warn("Analytics weekly recap catch-up failed exceptionType={} exceptionMessage={}",
                    sanitizeForLog(exception.getClass().getName()),
                    sanitizeForLog(exception.getMessage()));
        }
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
        if (recapMarkRepository.existsByUserIdAndWeekStart(userId, weekStart)) {
            return false;
        }
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
        recapMarkRepository.save(new AnalyticsWeeklyRecapMarkEntity(userId, weekStart, Instant.now(clock)));
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
