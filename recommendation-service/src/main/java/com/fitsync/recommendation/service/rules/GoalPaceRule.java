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
 * R3 — goal pace, judged the way the Progress goal road judges it: the weekly rate since the
 * baseline (the first weigh-in on or after the goal's start) carried forward to a projected
 * date, compared with the goal date. Seven days either side of the goal date is on course,
 * earlier is ahead, later is behind, so the rule never says behind where the road says ahead.
 * A change opposite to the goal direction has no projected date and is behind; a weight
 * already at or past the target is reached. Needs a weight goal with target value/date, the
 * baseline, a latest weight, and at least one elapsed day of signal.
 */
@Service
public class GoalPaceRule implements Rule {

    static final String STATUS_AHEAD = "AHEAD";
    static final String STATUS_ON_TRACK = "ON_TRACK";
    static final String STATUS_BEHIND = "BEHIND";
    static final String STATUS_REACHED = "REACHED";

    /** The projected date must be this many days from the goal date to count as ahead or behind. */
    static final int ON_COURSE_SLACK_DAYS = 7;

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
        double remaining = target - latest;
        boolean reached = remaining == 0.0 || Math.signum(remaining) != Math.signum(requiredPerWeek);
        LocalDate projected = reached ? null : projectedDate(today, remaining, actualPerWeek);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("baselineWeightKg", context.getGoalBaselineWeightKg());
        facts.put("baselineOn", baselineOn);
        facts.put("latestWeightKg", context.getLatestWeightKg());
        facts.put("targetWeightKg", context.getCurrentGoalTargetValue());
        facts.put("targetDate", targetDate);
        facts.put("daysRemaining", ChronoUnit.DAYS.between(today, targetDate));
        facts.put("distanceKg", BigDecimal.valueOf(Math.abs(remaining)).setScale(1, RoundingMode.HALF_UP));
        facts.put("requiredRatePerWeekKg", roundRate(requiredPerWeek));
        facts.put("actualRatePerWeekKg", roundRate(actualPerWeek));
        if (projected != null) {
            facts.put("projectedDate", projected);
        }
        String status = reached ? STATUS_REACHED : status(projected, targetDate);
        facts.put("status", status);
        if (projected != null && STATUS_AHEAD.equals(status)) {
            facts.put("daysEarly", ChronoUnit.DAYS.between(projected, targetDate));
        } else if (projected != null && STATUS_BEHIND.equals(status)) {
            facts.put("daysLate", ChronoUnit.DAYS.between(targetDate, projected));
        }
        return Optional.of(new RuleOutcome(type(), facts));
    }

    /** Today plus the weeks the remaining kilos take at the actual rate, or null when the rate does not lead there. */
    private static LocalDate projectedDate(LocalDate today, double remaining, double actualPerWeek) {
        if (actualPerWeek == 0.0 || Math.signum(actualPerWeek) != Math.signum(remaining)) {
            return null;
        }
        return today.plusDays(Math.round(remaining / actualPerWeek * DAYS_PER_WEEK));
    }

    private static String status(LocalDate projected, LocalDate targetDate) {
        if (projected == null) {
            return STATUS_BEHIND;
        }
        long days = ChronoUnit.DAYS.between(targetDate, projected);
        if (days <= -ON_COURSE_SLACK_DAYS) {
            return STATUS_AHEAD;
        }
        return days >= ON_COURSE_SLACK_DAYS ? STATUS_BEHIND : STATUS_ON_TRACK;
    }

    private static BigDecimal roundRate(double ratePerWeek) {
        return BigDecimal.valueOf(ratePerWeek).setScale(2, RoundingMode.HALF_UP);
    }
}
