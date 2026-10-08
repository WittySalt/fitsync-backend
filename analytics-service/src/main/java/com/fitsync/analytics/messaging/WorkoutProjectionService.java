package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsWorkoutContributionEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.service.ActiveStreak;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.analytics.service.GoalProgressEvaluator;
import com.fitsync.analytics.service.UserZone;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects workout sessions into the per-day workout rows and the dashboard snapshot. Each session is
 * kept as a contribution row, so an update first subtracts the old contribution from its day and then
 * adds the new one (possibly on a different day), and a delete subtracts it. Events are applied once:
 * the processed-event table makes redelivery a no-op.
 */
@Service
public class WorkoutProjectionService {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsWorkoutContributionRepository workoutContributionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;
    private final GoalProgressEvaluator goalProgressEvaluator;
    private final Clock clock;

    public WorkoutProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                    AnalyticsWorkoutContributionRepository workoutContributionRepository,
                                    AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                    AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                    AnalyticsCacheInvalidationService cacheInvalidationService,
                                    DashboardSnapshotInitializer dashboardSnapshotInitializer,
                                    GoalProgressEvaluator goalProgressEvaluator,
                                    Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.workoutContributionRepository = workoutContributionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
        this.goalProgressEvaluator = goalProgressEvaluator;
        this.clock = clock;
    }

    @Transactional
    public void upsertWorkout(FitSyncDomainEvent<?> event,
                              UUID sessionId,
                              Instant startedAt,
                              Integer durationMinutes,
                              Integer caloriesBurned,
                              Integer exerciseCount,
                              boolean caloriesEstimated) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (sessionId == null || startedAt == null) {
            ProcessedEvents.record(processedEventRepository, event, clock);
            return;
        }

        AnalyticsUserDashboardSnapshotEntity snapshot = findOrCreateDashboardSnapshot(event.userId(), event.occurredAt());
        ZoneId userZone = UserZone.of(snapshot.getTimezone());
        LocalDate activityDate = startedAt.atZone(userZone).toLocalDate();
        AnalyticsWorkoutContributionEntity existingContribution = workoutContributionRepository.findById(sessionId).orElse(null);

        if (existingContribution != null) {
            applyContributionDelta(event.userId(), existingContribution.getActivityDate(), existingContribution, null, event.occurredAt());
        }

        AnalyticsWorkoutContributionEntity newContribution = existingContribution != null
                ? existingContribution
                : new AnalyticsWorkoutContributionEntity();
        newContribution.setSessionId(sessionId);
        newContribution.setUserId(event.userId());
        newContribution.setActivityDate(activityDate);
        newContribution.setDurationMinutes(durationMinutes);
        newContribution.setCaloriesBurned(caloriesBurned);
        newContribution.setExerciseCount(exerciseCount);
        newContribution.setCaloriesEstimated(caloriesEstimated);
        newContribution.setLastAppliedAt(resolveOccurredAt(event.occurredAt(), startedAt));
        workoutContributionRepository.save(newContribution);

        applyContributionDelta(event.userId(), activityDate, null, newContribution, event.occurredAt());
        recomputeWorkoutGoals(event.userId());
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(event.userId());
    }

    @Transactional
    public void deleteWorkout(FitSyncDomainEvent<?> event, UUID sessionId) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        if (sessionId == null) {
            ProcessedEvents.record(processedEventRepository, event, clock);
            return;
        }

        AnalyticsWorkoutContributionEntity existingContribution = workoutContributionRepository.findById(sessionId).orElse(null);
        if (existingContribution != null) {
            applyContributionDelta(event.userId(), existingContribution.getActivityDate(), existingContribution, null, event.occurredAt());
            workoutContributionRepository.delete(existingContribution);
            recomputeWorkoutGoals(event.userId());
        }
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(event.userId());
    }

    private void applyContributionDelta(UUID userId,
                                        LocalDate activityDate,
                                        AnalyticsWorkoutContributionEntity oldContribution,
                                        AnalyticsWorkoutContributionEntity newContribution,
                                        Instant occurredAt) {
        AnalyticsUserDailyWorkoutEntity daily = dailyWorkoutRepository.findByUserIdAndActivityDate(userId, activityDate)
                .orElseGet(() -> createDailyWorkoutRow(userId, activityDate));

        daily.setCompletedWorkoutCount(Math.max(
                defaultInt(daily.getCompletedWorkoutCount()) - contributionCount(oldContribution) + contributionCount(newContribution), 0));
        daily.setTotalWorkoutMinutes(Math.max(
                defaultInt(daily.getTotalWorkoutMinutes()) - contributionDuration(oldContribution) + contributionDuration(newContribution), 0));
        daily.setTotalCaloriesBurned(Math.max(
                defaultInt(daily.getTotalCaloriesBurned()) - contributionCalories(oldContribution) + contributionCalories(newContribution), 0));
        daily.setEstimatedCaloriesBurned(Math.max(
                defaultInt(daily.getEstimatedCaloriesBurned()) - estimatedCalories(oldContribution) + estimatedCalories(newContribution), 0));
        daily.setTotalExerciseCount(Math.max(
                defaultInt(daily.getTotalExerciseCount()) - contributionExerciseCount(oldContribution) + contributionExerciseCount(newContribution), 0));
        daily.setProjectionUpdatedAt(resolveOccurredAt(occurredAt, null));
        dailyWorkoutRepository.save(daily);

        syncDashboardSnapshot(userId, occurredAt);
    }

    private AnalyticsUserDailyWorkoutEntity createDailyWorkoutRow(UUID userId, LocalDate activityDate) {
        AnalyticsUserDailyWorkoutEntity entity = new AnalyticsUserDailyWorkoutEntity();
        entity.setUserId(userId);
        entity.setActivityDate(activityDate);
        entity.setCompletedWorkoutCount(0);
        entity.setTotalWorkoutMinutes(0);
        entity.setTotalCaloriesBurned(0);
        entity.setEstimatedCaloriesBurned(0);
        entity.setTotalExerciseCount(0);
        entity.setRolling7DayWorkoutCount(0);
        entity.setRolling28DayWorkoutCount(0);
        entity.setProjectionUpdatedAt(Instant.now(clock));
        return entity;
    }

    private void syncDashboardSnapshot(UUID userId, Instant occurredAt) {
        AnalyticsUserDashboardSnapshotEntity snapshot = findOrCreateDashboardSnapshot(userId, occurredAt);
        ZoneId userZone = UserZone.of(snapshot.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(userZone));
        // The snapshot's week fields are a rolling seven days ending today, not the calendar week.
        LocalDate windowStart = today.minusDays(6);

        List<AnalyticsUserDailyWorkoutEntity> lastSevenDays = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(userId, windowStart, today);

        int weekWorkoutCount = lastSevenDays.stream()
                .mapToInt(row -> defaultInt(row.getCompletedWorkoutCount()))
                .sum();
        int weekWorkoutMinutes = lastSevenDays.stream()
                .mapToInt(row -> defaultInt(row.getTotalWorkoutMinutes()))
                .sum();
        int activeStreakDays = ActiveStreak.days(dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
                        userId, today.minusDays(ActiveStreak.WINDOW_DAYS), today), today);
        int todayWorkoutCount = lastSevenDays.stream()
                .filter(row -> today.equals(row.getActivityDate()))
                .mapToInt(row -> defaultInt(row.getCompletedWorkoutCount()))
                .findFirst()
                .orElse(0);

        snapshot.setTodayWorkoutCount(todayWorkoutCount);
        snapshot.setWeekWorkoutCount(weekWorkoutCount);
        snapshot.setWeekWorkoutMinutes(weekWorkoutMinutes);
        snapshot.setActiveStreakDays(activeStreakDays);
        snapshot.setGeneratedAt(resolveOccurredAt(occurredAt, null));
        dashboardSnapshotRepository.save(snapshot);
    }

    private void recomputeWorkoutGoals(UUID userId) {
        goalProgressEvaluator.recompute(
                userId,
                Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY),
                goalProgressEvaluator.todayForUser(userId));
    }

    private AnalyticsUserDashboardSnapshotEntity findOrCreateDashboardSnapshot(UUID userId, Instant occurredAt) {
        dashboardSnapshotInitializer.insertIfMissing(userId, resolveOccurredAt(occurredAt, null));
        return dashboardSnapshotRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));
    }

    private int contributionCount(AnalyticsWorkoutContributionEntity contribution) {
        return contribution == null ? 0 : 1;
    }

    private int contributionDuration(AnalyticsWorkoutContributionEntity contribution) {
        return contribution == null || contribution.getDurationMinutes() == null ? 0 : contribution.getDurationMinutes();
    }

    private int contributionCalories(AnalyticsWorkoutContributionEntity contribution) {
        return contribution == null || contribution.getCaloriesBurned() == null ? 0 : contribution.getCaloriesBurned();
    }

    /** The calories of a workout whose calories the server estimated; zero when the person gave them. */
    private int estimatedCalories(AnalyticsWorkoutContributionEntity contribution) {
        return contribution != null && contribution.isCaloriesEstimated() ? contributionCalories(contribution) : 0;
    }

    private int contributionExerciseCount(AnalyticsWorkoutContributionEntity contribution) {
        return contribution == null || contribution.getExerciseCount() == null ? 0 : contribution.getExerciseCount();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Instant resolveOccurredAt(Instant occurredAt, Instant fallback) {
        if (occurredAt != null) {
            return occurredAt;
        }
        if (fallback != null) {
            return fallback;
        }
        return Instant.now(clock);
    }
}
