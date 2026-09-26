package com.fitsync.recommendation.service.rules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R2 — weekly adherence: the user's workout-days-per-week target vs the active days in
 * the latest analytics weekly recap ("targeted N, did M, K short"). Purely a
 * comparison of two consumed facts — nothing is recomputed here.
 */
@Service
public class WeeklyAdherenceRule implements Rule {

    static final String STATUS_MET = "MET";
    static final String STATUS_MISSED = "MISSED";

    @Override
    public String type() {
        return RecommendationTypes.WEEKLY_ADHERENCE;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        Integer targetDays = context.getWorkoutDaysPerWeek();
        Integer activeDays = context.getRecentActiveDays();
        if (targetDays == null || targetDays <= 0 || activeDays == null) {
            return Optional.empty();
        }

        int shortfall = Math.max(0, targetDays - activeDays);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("targetDaysPerWeek", targetDays);
        facts.put("activeDays", activeDays);
        facts.put("shortfallDays", shortfall);
        facts.put("status", shortfall == 0 ? STATUS_MET : STATUS_MISSED);
        if (context.getRecentWorkoutCount() != null) {
            facts.put("workoutCount", context.getRecentWorkoutCount());
        }
        if (context.getRecentWeekStart() != null) {
            facts.put("weekStart", context.getRecentWeekStart());
        }
        return Optional.of(new RuleOutcome(type(), facts));
    }
}
