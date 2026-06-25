package com.fitsync.analytics.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsDailyRollupUserProcessor {

    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsRebuildService rebuildService;
    private final GoalProgressEvaluator goalProgressEvaluator;

    public AnalyticsDailyRollupUserProcessor(AnalyticsUserGoalProgressRepository goalProgressRepository,
                                             AnalyticsRebuildService rebuildService,
                                             GoalProgressEvaluator goalProgressEvaluator) {
        this.goalProgressRepository = goalProgressRepository;
        this.rebuildService = rebuildService;
        this.goalProgressEvaluator = goalProgressEvaluator;
    }

    @Transactional
    public void rollupUser(UUID userId, LocalDate todayUtc, Instant evaluatedAt) {
        LocalDate today = goalProgressEvaluator.todayForUser(userId);
        updateActiveGoalCountdowns(userId, today, evaluatedAt);
        goalProgressEvaluator.recompute(userId, GoalProgressEvaluator.ALL_GOAL_TYPES, today);
        rebuildService.rebuildDashboard(userId);
    }

    private void updateActiveGoalCountdowns(UUID userId, LocalDate todayUtc, Instant evaluatedAt) {
        List<AnalyticsUserGoalProgressEntity> activeGoals =
                goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(
                        userId, GoalExpiryPolicy.ACTIVE_STATUS);
        activeGoals.stream()
                .filter(goal -> applyGoalCountdown(goal, todayUtc, evaluatedAt))
                .forEach(goalProgressRepository::save);
    }

    private boolean applyGoalCountdown(AnalyticsUserGoalProgressEntity goal,
                                       LocalDate todayUtc,
                                       Instant evaluatedAt) {
        if (GoalExpiryPolicy.isExpired(goal.getTargetDate(), todayUtc)) {
            goal.setGoalStatus(GoalExpiryPolicy.EXPIRED_STATUS);
            goal.setOnTrack(false);
            goal.setProgressRatio(null);
            goal.setCurrentValue(null);
            goal.setDaysRemaining(null);
            goal.setLastEvaluatedAt(evaluatedAt);
            return true;
        }

        Integer daysRemaining = GoalExpiryPolicy.daysRemaining(goal.getTargetDate(), todayUtc);
        if (!Objects.equals(goal.getDaysRemaining(), daysRemaining)) {
            goal.setDaysRemaining(daysRemaining);
            goal.setLastEvaluatedAt(evaluatedAt);
            return true;
        }
        return false;
    }
}
