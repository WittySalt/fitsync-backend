package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns one user-service progress insight, and optionally the calories from a nutrition insight,
 * into the numbered facts a progress reading is built and checked against.
 *
 * <p>Dates are written the way the page writes them, a day and a short month, and the year is added
 * only when it differs from the reference date's year, so a sentence that quotes a date quotes the
 * same characters a fact holds.
 */
@Component
public class ProgressFactsBuilder {

    /** The Progress card a fact belongs to, so the page can scroll to it from a citation. */
    static final String CARD_GOAL = "GOAL";
    static final String CARD_TRAINING = "TRAINING";
    static final String CARD_FOOD = "FOOD";

    /** English short month names, written out because the JDK's own short form of September is "Sep". */
    private static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec"};

    /**
     * @param nutrition the same week's nutrition insight, or null when it is not available; only its
     *                  calories are used
     */
    public List<ReadingFact> build(ProgressInsight insight, NutritionInsight nutrition) {
        List<ReadingFact> chosen = new ArrayList<>();
        addWeight(chosen, insight);
        addWeeklyRate(chosen, insight);
        addGoal(chosen, insight);
        // Weight, rate and goal are the Goal card's figures; calories are the Food card's.
        chosen.replaceAll(fact -> fact.withCard(CARD_GOAL));
        ReadingFact calories = NutritionFactsBuilder.caloriesFact(nutrition);
        if (calories != null) {
            chosen.add(calories.withCard(CARD_FOOD));
        }
        return ReadingFactsBuilder.numbered(chosen);
    }

    /**
     * The weight, rate and goal group the coach reads, taken from the same insight and written with
     * the same formatting as the facts above, so the coach and the page reading cannot disagree about
     * a figure or a date. Empty when the insight holds neither a weight nor a goal.
     */
    public static Map<String, Object> paceFacts(ProgressInsight insight) {
        Map<String, Object> pace = new LinkedHashMap<>();
        if (insight == null) {
            return pace;
        }
        if (insight.weightKg() != null) {
            pace.put("latestWeightKg", insight.weightKg().setScale(1, RoundingMode.HALF_UP).toPlainString());
            if (insight.measuredOn() != null) {
                pace.put("weighedOn", shortDate(insight.measuredOn(), false));
            }
        }
        BigDecimal rate = insight.weeklyRateKg();
        if (rate != null) {
            int direction = rate.setScale(2, RoundingMode.HALF_UP).signum();
            pace.put("weeklyChangeKg", ReadingFactsBuilder.upToTwoDecimals(rate.abs()));
            pace.put("weeklyChangeDirection", direction < 0 ? "down" : direction > 0 ? "up" : "unchanged");
            pace.put("weeklyChangeWindowDays", ProgressInsight.PERIOD_DAYS);
        }
        if (insight.goalWeightKg() != null) {
            pace.put("targetWeightKg", ReadingFactsBuilder.upToOneDecimal(insight.goalWeightKg()));
            if (insight.goalDate() != null) {
                pace.put("targetDate", dateFor(insight.goalDate(), insight));
            }
            if (insight.projectedDate() != null) {
                pace.put("projectedDateAtThisRate", dateFor(insight.projectedDate(), insight));
            }
            String status = paceStatus(insight);
            if (status != null) {
                pace.put("status", status);
            }
        }
        return pace;
    }

    /** Where the projection lands against the goal date, or null when there is nothing to compare. */
    private static String paceStatus(ProgressInsight insight) {
        LocalDate goalDate = insight.goalDate();
        if (goalDate == null || insight.weeklyRateKg() == null) {
            return null;
        }
        LocalDate projected = insight.projectedDate();
        if (projected == null || projected.isAfter(goalDate)) {
            return "BEHIND";
        }
        return projected.isBefore(goalDate) ? "AHEAD" : "ON_TRACK";
    }

    private static void addWeight(List<ReadingFact> facts, ProgressInsight insight) {
        BigDecimal weight = insight.weightKg();
        if (weight == null) {
            return;
        }
        Integer entries = insight.entries();
        String detail = entries == null || entries <= 0
                ? ""
                : entries + (entries == 1 ? " weigh-in" : " weigh-ins") + " in eight weeks";
        facts.add(new ReadingFact(0, "WEIGHT", "Weight",
                weight.setScale(1, RoundingMode.HALF_UP).toPlainString(), "kg", weight,
                insight.measuredOn() == null ? "" : shortDate(insight.measuredOn(), false), detail));
    }

    private static void addWeeklyRate(List<ReadingFact> facts, ProgressInsight insight) {
        BigDecimal rate = insight.weeklyRateKg();
        if (rate == null) {
            return;
        }
        // A rate that rounds to zero is shown as zero, so its direction is read from the rounded value.
        int direction = rate.setScale(2, RoundingMode.HALF_UP).signum();
        String note = direction < 0 ? "Down" : direction > 0 ? "Up" : "Unchanged";
        facts.add(new ReadingFact(0, "WEEKLY_RATE", "Change a week",
                ReadingFactsBuilder.upToTwoDecimals(rate.abs()), "kg", rate.abs(), note,
                "over the last " + ProgressInsight.PERIOD_DAYS + " days"));
    }

    private static void addGoal(List<ReadingFact> facts, ProgressInsight insight) {
        BigDecimal goal = insight.goalWeightKg();
        if (goal == null) {
            return;
        }
        LocalDate goalDate = insight.goalDate();
        LocalDate projected = insight.projectedDate();
        facts.add(new ReadingFact(0, "GOAL", "Goal", ReadingFactsBuilder.upToOneDecimal(goal), "kg", goal,
                goalDate == null ? "" : "by " + dateFor(goalDate, insight),
                projected == null ? "" : dateFor(projected, insight) + " at this rate"));
    }

    /** A date as the page writes it, with the year only when it is not the reference date's year. */
    static String dateFor(LocalDate date, ProgressInsight insight) {
        LocalDate reference = insight.referenceDate();
        return shortDate(date, reference != null && date.getYear() != reference.getYear());
    }

    /** {@code 28 Sept}, or {@code 10 Jan 2027} when the year is asked for. */
    static String shortDate(LocalDate date, boolean withYear) {
        String text = date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1];
        return withYear ? text + " " + date.getYear() : text;
    }
}
