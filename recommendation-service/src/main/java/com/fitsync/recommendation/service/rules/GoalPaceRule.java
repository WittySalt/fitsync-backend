package com.fitsync.recommendation.service.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R3 — goal pace: compares the actual weekly weight-change rate since the goal baseline
 * against the rate required to hit the target by the target date. Direction-aware: a
 * change opposite to the goal direction is always BEHIND. Needs a weight goal with
 * target value/date, the baseline captured at goal-set time, a latest weight, and at
 * least one elapsed day of signal.
 */
@Service
public class GoalPaceRule implements Rule {

    static final String STATUS_AHEAD = "AHEAD";
    static final String STATUS_ON_TRACK = "ON_TRACK";
    static final String STATUS_BEHIND = "BEHIND";

    // actual/required rate ratio thresholds; ±10% counts as on track.
    private static final double AHEAD_RATIO = 1.1;
    private static final double ON_TRACK_RATIO = 0.9;
    private static final double DAYS_PER_WEEK = 7.0;

    private final Clock clock;

    public GoalPaceRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return RecommendationTypes.GOAL_PACE;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        if (!GoalTypes.isWeightGoal(context.getCurrentGoalType())
                || context.getCurrentGoalTargetValue() == null
                || context.getCurrentGoalTargetDate() == null
                || context.getGoalBaselineWeightKg() == null
                || context.getGoalBaselineOn() == null
                || context.getLatestWeightKg() == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(clock.withZone(UserTimeZones.resolve(context.getTimezone())));
        LocalDate baselineOn = context.getGoalBaselineOn();
        LocalDate targetDate = context.getCurrentGoalTargetDate();
        long elapsedDays = ChronoUnit.DAYS.between(baselineOn, today);
        long totalDays = ChronoUnit.DAYS.between(baselineOn, targetDate);
        if (elapsedDays < 1 || totalDays < 1 || today.isAfter(targetDate)) {
            return Optional.empty();
        }

        double baseline = context.getGoalBaselineWeightKg().doubleValue();
        double latest = context.getLatestWeightKg().doubleValue();
        double target = context.getCurrentGoalTargetValue().doubleValue();
        double requiredPerWeek = (target - baseline) / totalDays * DAYS_PER_WEEK;
        if (requiredPerWeek == 0.0) {
            return Optional.empty();
        }
        double actualPerWeek = (latest - baseline) / elapsedDays * DAYS_PER_WEEK;
        double paceRatio = actualPerWeek / requiredPerWeek;

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("baselineWeightKg", context.getGoalBaselineWeightKg());
        facts.put("latestWeightKg", context.getLatestWeightKg());
        facts.put("targetWeightKg", context.getCurrentGoalTargetValue());
        facts.put("targetDate", targetDate);
        facts.put("daysRemaining", ChronoUnit.DAYS.between(today, targetDate));
        facts.put("requiredRatePerWeekKg", roundRate(requiredPerWeek));
        facts.put("actualRatePerWeekKg", roundRate(actualPerWeek));
        facts.put("status", status(paceRatio));
        return Optional.of(new RuleOutcome(type(), facts));
    }

    private static String status(double paceRatio) {
        if (paceRatio >= AHEAD_RATIO) {
            return STATUS_AHEAD;
        }
        if (paceRatio >= ON_TRACK_RATIO) {
            return STATUS_ON_TRACK;
        }
        return STATUS_BEHIND;
    }

    private static BigDecimal roundRate(double ratePerWeek) {
        return BigDecimal.valueOf(ratePerWeek).setScale(2, RoundingMode.HALF_UP);
    }
}
