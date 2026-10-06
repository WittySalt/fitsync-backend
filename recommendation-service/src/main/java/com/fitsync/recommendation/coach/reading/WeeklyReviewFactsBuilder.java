package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Turns the Training, Goal and Food endpoints' figures for one Monday week into the numbered facts a
 * weekly review is written and checked against.
 *
 * <p>Pure and deterministic like the page fact builders. Each fact carries the card it came from, so a
 * client can scroll from a citation to the card. A figure the endpoint did not send is left out, and a
 * week in which nothing was recorded has no facts at all rather than a row of zeros.
 *
 * <p>Planned days done comes from workout-service's weekly insight for the same week ("3 of 4 planned
 * days"), and is left out when no plan was active.
 *
 * <p>Today is still going (progress review fixes, section 1). The Food page already averages the days
 * that are over, so on the current week the calorie and protein facts are those averages; before one day
 * of the week is over they read what today holds so far instead, with the detail {@value #TODAY_SO_FAR}.
 * Sessions and minutes of the current week count today, from the Training page's week in progress.
 */
@Component
public class WeeklyReviewFactsBuilder {

    /** The detail of a calorie or protein fact that holds only today, which is still going. */
    static final String TODAY_SO_FAR = "today so far";

    private static final int MAX_TEXT = 80;
    private static final int MAX_NAMES = 3;

    /**
     * @param training the Training endpoint's body for the week, or null when it could not be read
     * @param goal     the Goal endpoint's body for the week, or null
     * @param food     the Food endpoint's body for the week, or null
     */
    public List<ReadingFact> build(JsonNode training, JsonNode goal, JsonNode food) {
        return build(training, goal, food, false);
    }

    /**
     * @param current true for the week that holds today
     */
    public List<ReadingFact> build(JsonNode training, JsonNode goal, JsonNode food, boolean current) {
        return build(training, goal, food, current, null);
    }

    /**
     * @param weekly workout-service's weekly insight for the same week, or null when it could not be read
     */
    public List<ReadingFact> build(JsonNode training, JsonNode goal, JsonNode food, boolean current,
                                   JsonNode weekly) {
        List<ReadingFact> chosen = new ArrayList<>();
        if (training != null) {
            addTraining(chosen, training.path("hero"), current ? training.path("load").path("currentWeek") : null);
        }
        if (weekly != null) {
            addPlannedDays(chosen, weekly);
        }
        if (food != null) {
            if (!addFood(chosen, food.path("hero")) && current) {
                addToday(chosen, today(food));
            }
        }
        if (goal != null) {
            addWeight(chosen, goal);
        }
        return ReadingFactsBuilder.numbered(chosen);
    }

    /** What today holds so far on the Food page of the current week, null when it does not say. */
    static WeeklyReviewView.Today today(JsonNode food) {
        if (food == null) {
            return null;
        }
        JsonNode week = food.path("currentWeek");
        Integer kcal = whole(week.path("eatenTodaySoFarKcal"));
        BigDecimal protein = decimal(week.path("proteinTodaySoFarGrams"));
        return kcal == null && protein == null ? null : new WeeklyReviewView.Today(kcal, protein);
    }

    private static void addTraining(List<ReadingFact> facts, JsonNode hero, JsonNode weekSoFar) {
        Integer sessions = whole(hero.path("sessions").path("value"));
        Integer minutesSoFar = null;
        if (weekSoFar != null && weekSoFar.isObject()) {
            // The head figures leave today out; the week so far counts it.
            Integer sessionsSoFar = whole(weekSoFar.path("sessions"));
            if (sessionsSoFar != null) {
                sessions = sessionsSoFar;
                minutesSoFar = whole(weekSoFar.path("minutes"));
            }
        }
        if (sessions == null || sessions <= 0) {
            return;
        }
        Integer earlierSessions = whole(hero.path("sessions").path("previous"));
        facts.add(new ReadingFact(0, "SESSIONS", "Sessions", String.valueOf(sessions),
                sessions == 1 ? "session" : "sessions", BigDecimal.valueOf(sessions),
                earlierSessions == null ? "" : countChangeNote(sessions, earlierSessions, ""),
                earlierSessions == null ? "" : earlierSessions + " the week before", ProgressFactsBuilder.CARD_TRAINING));

        Integer minutes = minutesSoFar != null ? minutesSoFar : whole(hero.path("minutes").path("value"));
        if (minutes != null && minutes > 0) {
            Integer earlierMinutes = whole(hero.path("minutes").path("previous"));
            facts.add(new ReadingFact(0, "MINUTES", "Minutes trained", String.valueOf(minutes), "min",
                    BigDecimal.valueOf(minutes),
                    earlierMinutes == null ? "" : countChangeNote(minutes, earlierMinutes, " min"),
                    earlierMinutes == null ? "" : earlierMinutes + " min the week before",
                    ProgressFactsBuilder.CARD_TRAINING));
        }

        Integer records = whole(hero.path("records").path("value"));
        if (records != null && records > 0) {
            facts.add(new ReadingFact(0, "RECORDS", records == 1 ? "Record" : "Records", String.valueOf(records),
                    "", BigDecimal.valueOf(records), "", names(hero.path("records").path("exercises")),
                    ProgressFactsBuilder.CARD_TRAINING));
        }
    }

    /** "3 of 4 planned days", when a plan scheduled days in the week. */
    private static void addPlannedDays(List<ReadingFact> facts, JsonNode weekly) {
        Integer planned = whole(weekly.path("plannedDays"));
        Integer done = whole(weekly.path("plannedDaysDone"));
        if (planned == null || planned <= 0 || done == null || done < 0) {
            return;
        }
        facts.add(new ReadingFact(0, "PLANNED_DAYS", "Planned days done", String.valueOf(done),
                "of " + planned, BigDecimal.valueOf(done), "",
                done + " of " + planned + " planned days", ProgressFactsBuilder.CARD_TRAINING));
    }

    /** The food facts over the logged days that are over; false when there is not one. */
    private static boolean addFood(List<ReadingFact> facts, JsonNode hero) {
        Integer logged = whole(hero.path("daysLogged").path("value"));
        if (logged == null || logged <= 0) {
            return false;
        }
        String onLogged = "on " + logged + (logged == 1 ? " logged day" : " logged days");
        BigDecimal eaten = decimal(hero.path("eatenPerDay").path("value"));
        if (eaten != null && eaten.signum() > 0) {
            BigDecimal earlier = decimal(hero.path("eatenPerDay").path("previous"));
            facts.add(new ReadingFact(0, "CALORIES_PER_DAY", "Calories a day", ReadingFactsBuilder.wholeWithCommas(eaten),
                    "kcal", eaten, earlier == null ? "" : ReadingFactsBuilder.percentChangeNote(eaten, earlier),
                    onLogged, ProgressFactsBuilder.CARD_FOOD));
        }
        BigDecimal protein = decimal(hero.path("proteinPerDay").path("value"));
        if (protein != null && protein.signum() > 0) {
            BigDecimal earlier = decimal(hero.path("proteinPerDay").path("previous"));
            facts.add(new ReadingFact(0, "PROTEIN_PER_DAY", "Protein a day", ReadingFactsBuilder.upToOneDecimal(protein),
                    "g", protein, earlier == null ? "" : ReadingFactsBuilder.percentChangeNote(protein, earlier),
                    onLogged, ProgressFactsBuilder.CARD_FOOD));
        }
        BigDecimal balance = decimal(hero.path("balancePerDay").path("value"));
        if (balance != null) {
            int sign = balance.setScale(0, RoundingMode.HALF_UP).signum();
            facts.add(new ReadingFact(0, "BALANCE_PER_DAY", "Balance a day",
                    ReadingFactsBuilder.wholeWithCommas(balance.abs()), "kcal", balance.abs(),
                    sign < 0 ? "Under" : sign > 0 ? "Over" : "Even",
                    "eaten against energy used, " + onLogged, ProgressFactsBuilder.CARD_FOOD));
        }
        return true;
    }

    /** Before a day of the week is over: what today holds so far, never compared with anything. */
    private static void addToday(List<ReadingFact> facts, WeeklyReviewView.Today today) {
        if (today == null) {
            return;
        }
        if (today.eatenKcal() != null && today.eatenKcal() > 0) {
            BigDecimal kcal = BigDecimal.valueOf(today.eatenKcal());
            facts.add(new ReadingFact(0, "CALORIES_PER_DAY", "Calories today", ReadingFactsBuilder.wholeWithCommas(kcal),
                    "kcal", kcal, "", TODAY_SO_FAR, ProgressFactsBuilder.CARD_FOOD));
        }
        if (today.proteinGrams() != null && today.proteinGrams().signum() > 0) {
            facts.add(new ReadingFact(0, "PROTEIN_PER_DAY", "Protein today",
                    ReadingFactsBuilder.upToOneDecimal(today.proteinGrams()), "g", today.proteinGrams(), "",
                    TODAY_SO_FAR, ProgressFactsBuilder.CARD_FOOD));
        }
    }

    private static void addWeight(List<ReadingFact> facts, JsonNode goal) {
        BigDecimal change = decimal(goal.path("hero").path("changeKg"));
        if (change == null || !weighedIn(goal.path("weight"))) {
            return;
        }
        int direction = change.setScale(2, RoundingMode.HALF_UP).signum();
        BigDecimal average = latestWeeklyAverage(goal.path("weight").path("weekly"));
        facts.add(new ReadingFact(0, "WEIGHT_CHANGE", "Weight change", ReadingFactsBuilder.upToTwoDecimals(change.abs()),
                "kg", change.abs(), direction < 0 ? "Down" : direction > 0 ? "Up" : "Unchanged",
                average == null ? "" : "weekly average " + ReadingFactsBuilder.upToOneDecimal(average) + " kg",
                ProgressFactsBuilder.CARD_GOAL));
    }

    /** True when the week holds at least one weigh-in, by the daily rows or the weekly counts. */
    private static boolean weighedIn(JsonNode weight) {
        if (weight.path("daily").isArray() && !weight.path("daily").isEmpty()) {
            return true;
        }
        for (JsonNode week : weight.path("weekly")) {
            Integer weighIns = whole(week.path("weighIns"));
            if (weighIns != null && weighIns > 0) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal latestWeeklyAverage(JsonNode weekly) {
        BigDecimal latest = null;
        for (JsonNode week : weekly) {
            BigDecimal average = decimal(week.path("avgKg"));
            if (average != null) {
                latest = average;
            }
        }
        return latest;
    }

    private static String countChangeNote(int current, int earlier, String unit) {
        int diff = current - earlier;
        if (diff == 0) {
            return "Unchanged";
        }
        return (diff > 0 ? "Up " : "Down ") + Math.abs(diff) + unit;
    }

    private static String names(JsonNode array) {
        List<String> names = new ArrayList<>();
        for (JsonNode name : array) {
            String cleaned = name.asText("").replaceAll("[\\p{Cntrl}]+", " ").strip();
            if (!cleaned.isEmpty()) {
                names.add(cleaned);
            }
            if (names.size() >= MAX_NAMES) {
                break;
            }
        }
        String joined = String.join(", ", names);
        return joined.length() > MAX_TEXT ? joined.substring(0, MAX_TEXT) : joined;
    }

    private static Integer whole(JsonNode node) {
        BigDecimal value = decimal(node);
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }
}
