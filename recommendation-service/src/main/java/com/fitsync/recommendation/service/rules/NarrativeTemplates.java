package com.fitsync.recommendation.service.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * RC-5 — the hand-written narrative template library (README §5.2): one small set of
 * templates per rule type, chosen by the rule's status facts, with the rule numbers
 * filling the slots. This is the funnel's template stage — zero AI, zero pipeline —
 * and the guaranteed fallback when the AI tier is unavailable.
 */
@Service
public class NarrativeTemplates {

    /**
     * Phrase a rule outcome. Every {@link RecommendationTypes} value has templates
     * here, so a fired rule always gets a narrative.
     */
    public String render(RuleOutcome outcome) {
        Map<String, Object> facts = outcome.facts();
        return switch (outcome.type()) {
            case RecommendationTypes.NUTRITION_GAP -> nutritionGap(facts);
            case RecommendationTypes.WEEKLY_ADHERENCE -> weeklyAdherence(facts);
            case RecommendationTypes.GOAL_PACE -> goalPace(facts);
            case RecommendationTypes.STREAK_NUDGE -> streakNudge(facts);
            case RecommendationTypes.COLD_START -> coldStart(facts);
            case RecommendationTypes.PERSONAL_RECORD -> personalRecord(facts);
            case RecommendationTypes.LOGGING_REMINDER -> loggingReminder(facts);
            default -> throw new IllegalArgumentException("No templates for type " + outcome.type());
        };
    }

    // T1-T3 — daily nutrition gap.
    private static String nutritionGap(Map<String, Object> facts) {
        int remaining = intFact(facts, "remainingCalories");
        int target = intFact(facts, "targetCalories");
        if (remaining <= 0) {
            return "You've hit today's %d kcal budget (%d kcal over). Lighter choices for the rest of the day."
                    .formatted(target, -remaining);
        }
        BigDecimal proteinRemaining = decimalFact(facts, "proteinRemainingGrams");
        if (proteinRemaining != null && proteinRemaining.signum() > 0) {
            return "You have %d kcal left today, and you're %sg short of your %sg protein target — favor a protein-rich meal."
                    .formatted(remaining, proteinRemaining.toPlainString(),
                            decimalFact(facts, "proteinTargetGrams").toPlainString());
        }
        return "You have %d kcal left of today's %d kcal target.".formatted(remaining, target);
    }

    // T4-T5 — weekly adherence.
    private static String weeklyAdherence(Map<String, Object> facts) {
        int targetDays = intFact(facts, "targetDaysPerWeek");
        int activeDays = intFact(facts, "activeDays");
        if (WeeklyAdherenceRule.STATUS_MET.equals(facts.get("status"))) {
            return "Weekly goal met: %d of %d training days done — great consistency."
                    .formatted(activeDays, targetDays);
        }
        return "You trained %d of %d planned days last week — %d more gets you to target this week."
                .formatted(activeDays, targetDays, intFact(facts, "shortfallDays"));
    }

    // T6-T8 — goal pace.
    private static String goalPace(Map<String, Object> facts) {
        String actual = rate(facts, "actualRatePerWeekKg");
        String required = rate(facts, "requiredRatePerWeekKg");
        String target = decimalFact(facts, "targetWeightKg").toPlainString();
        LocalDate targetDate = (LocalDate) facts.get("targetDate");
        return switch (String.valueOf(facts.get("status"))) {
            case GoalPaceRule.STATUS_AHEAD ->
                    "You're ahead of pace: %s kg/week vs the %s kg/week needed for %s kg by %s."
                            .formatted(actual, required, target, targetDate);
            case GoalPaceRule.STATUS_ON_TRACK ->
                    "Right on track for %s kg by %s — keep the current routine going."
                            .formatted(target, targetDate);
            default ->
                    "Progress is behind pace (%s vs %s kg/week needed). Small adjustments now beat big ones later."
                            .formatted(actual, required);
        };
    }

    // T9-T10 — streak nudge.
    private static String streakNudge(Map<String, Object> facts) {
        int streakDays = intFact(facts, "streakDays");
        Object nextMilestone = facts.get("nextMilestone");
        if (nextMilestone == null) {
            return "%d days straight — you've outgrown every milestone. Keep it rolling.".formatted(streakDays);
        }
        return "%d-day streak! %d more days to reach the %s-day milestone."
                .formatted(streakDays, intFact(facts, "daysToNextMilestone"), nextMilestone);
    }

    // T11-T13 — cold start.
    private static String coldStart(Map<String, Object> facts) {
        Object goalType = facts.get("goalType");
        Object daysPerWeek = facts.get("workoutDaysPerWeek");
        if (goalType != null && daysPerWeek != null) {
            return ("Your %s goal and %s training days a week are set — log your first workout or meal "
                    + "to unlock personalized insights.").formatted(goalType, daysPerWeek);
        }
        if (facts.get("focusAreas") instanceof List<?> focusAreas && !focusAreas.isEmpty()) {
            return "Ready when you are: a first %s session would kick off your personalized recommendations."
                    .formatted(String.valueOf(focusAreas.get(0)).toLowerCase(java.util.Locale.ROOT));
        }
        return "Log your first workout or meal to unlock personalized recommendations.";
    }

    // T14-T15 — PR celebration.
    private static String personalRecord(Map<String, Object> facts) {
        String what = recordTypeLabel(String.valueOf(facts.get("recordType")));
        Object exercise = facts.get("exercise");
        BigDecimal value = decimalFact(facts, "value");
        String amount = value != null ? " (%s)".formatted(value.stripTrailingZeros().toPlainString()) : "";
        if (exercise != null) {
            return "New personal record: %s on %s%s — that's the payoff of consistent work."
                    .formatted(what, exercise, amount);
        }
        return "New personal record: %s%s — that's the payoff of consistent work.".formatted(what, amount);
    }

    private static String recordTypeLabel(String recordType) {
        return switch (recordType) {
            case "MAX_WEIGHT_KG" -> "heaviest weight";
            case "MAX_REPS" -> "most reps";
            case "MAX_SINGLE_SET_VOLUME_KG" -> "biggest single-set volume";
            case "ESTIMATED_ONE_REP_MAX_KG" -> "estimated one-rep max";
            case "BEST_SESSION_VOLUME_KG" -> "best session volume";
            // No case mapping (findsecbugs IMPROPER_UNICODE) — unknown types read fine as-is.
            default -> recordType.replace('_', ' ');
        };
    }

    // T16 — logging reminder.
    private static String loggingReminder(Map<String, Object> facts) {
        return "It's been %d days since your last logged meal — one quick log today gets the picture back."
                .formatted(intFact(facts, "daysSinceLastMealLog"));
    }

    private static int intFact(Map<String, Object> facts, String key) {
        return ((Number) facts.get(key)).intValue();
    }

    private static BigDecimal decimalFact(Map<String, Object> facts, String key) {
        return (BigDecimal) facts.get(key);
    }

    private static String rate(Map<String, Object> facts, String key) {
        return decimalFact(facts, key).abs().toPlainString();
    }
}
