package com.fitsync.analytics.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The nightly rollup for one user, in one transaction: expires goals past their target date and refreshes
 * the countdowns of the rest, re-evaluates every active goal, materializes the streak rollup and rebuilds
 * the dashboard snapshot.
 */
@Service
public class AnalyticsDailyRollupUserProcessor {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDailyRollupUserProcessor.class);

    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsRebuildService rebuildService;
    private final GoalProgressEvaluator goalProgressEvaluator;
    private final AnalyticsStreakRollupService streakRollupService;

    public AnalyticsDailyRollupUserProcessor(AnalyticsUserGoalProgressRepository goalProgressRepository,
                                             AnalyticsRebuildService rebuildService,
                                             GoalProgressEvaluator goalProgressEvaluator,
                                             AnalyticsStreakRollupService streakRollupService) {
        this.goalProgressRepository = goalProgressRepository;
        this.rebuildService = rebuildService;
        this.goalProgressEvaluator = goalProgressEvaluator;
        this.streakRollupService = streakRollupService;
    }

    /**
     * "Today" is resolved in the user's own zone rather than taken from the caller, so goals expire at the
     * user's midnight.
     */
    @Transactional
    public void rollupUser(UUID userId, Instant evaluatedAt) {
        LocalDate today = goalProgressEvaluator.todayForUser(userId);
        updateActiveGoalCountdowns(userId, today, evaluatedAt);
        goalProgressEvaluator.recompute(userId, GoalProgressEvaluator.ALL_GOAL_TYPES, today);
        // Before rebuildDashboard so its cache eviction also flushes streak reads materialized here.
        // The streak rollup runs in its own transaction and is tolerated on failure: a broken streak
        // materialization must not cost the user their nightly goal-countdown and dashboard refresh.
        try {
            streakRollupService.rollupUser(userId);
        } catch (RuntimeException exception) {
            log.warn("Analytics streak rollup failed userId={} exceptionType={} exceptionMessage={}",
                    sanitizeForLog(userId),
                    sanitizeForLog(exception.getClass().getName()),
                    sanitizeForLog(exception.getMessage()));
        }
        rebuildService.rebuildDashboard(userId);
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }

    private void updateActiveGoalCountdowns(UUID userId, LocalDate today, Instant evaluatedAt) {
        List<AnalyticsUserGoalProgressEntity> activeGoals =
                goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(
                        userId, GoalExpiryPolicy.ACTIVE_STATUS);
        activeGoals.stream()
                .filter(goal -> applyGoalCountdown(goal, today, evaluatedAt))
                .forEach(goalProgressRepository::save);
    }

    /** Returns whether the goal changed and needs saving. */
    private boolean applyGoalCountdown(AnalyticsUserGoalProgressEntity goal,
                                       LocalDate today,
                                       Instant evaluatedAt) {
        if (GoalExpiryPolicy.isExpired(goal.getTargetDate(), today)) {
            goal.setGoalStatus(GoalExpiryPolicy.EXPIRED_STATUS);
            goal.setOnTrack(false);
            goal.setProgressRatio(null);
            goal.setCurrentValue(null);
            goal.setDaysRemaining(null);
            goal.setLastEvaluatedAt(evaluatedAt);
            return true;
        }

        Integer daysRemaining = GoalExpiryPolicy.daysRemaining(goal.getTargetDate(), today);
        if (!Objects.equals(goal.getDaysRemaining(), daysRemaining)) {
            goal.setDaysRemaining(daysRemaining);
            goal.setLastEvaluatedAt(evaluatedAt);
            return true;
        }
        return false;
    }
}
