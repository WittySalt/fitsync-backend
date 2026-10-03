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
 * <p>Planned days done is not among the facts: none of the three endpoints sends it for a past week.
 */
@Component
public class WeeklyReviewFactsBuilder {

    private static final int MAX_TEXT = 80;
    private static final int MAX_NAMES = 3;

    /**
     * @param training the Training endpoint's body for the week, or null when it could not be read
     * @param goal     the Goal endpoint's body for the week, or null
     * @param food     the Food endpoint's body for the week, or null
     */
    public List<ReadingFact> build(JsonNode training, JsonNode goal, JsonNode food) {
        List<ReadingFact> chosen = new ArrayList<>();
        if (training != null) {
            addTraining(chosen, training.path("hero"));
        }
        if (food != null) {
            addFood(chosen, food.path("hero"));
        }
        if (goal != null) {
            addWeight(chosen, goal);
        }
        return ReadingFactsBuilder.numbered(chosen);
    }

    private static void addTraining(List<ReadingFact> facts, JsonNode hero) {
        Integer sessions = whole(hero.path("sessions").path("value"));
        if (sessions == null || sessions <= 0) {
            return;
        }
        Integer earlierSessions = whole(hero.path("sessions").path("previous"));
        facts.add(new ReadingFact(0, "SESSIONS", "Sessions", String.valueOf(sessions),
                sessions == 1 ? "session" : "sessions", BigDecimal.valueOf(sessions),
                earlierSessions == null ? "" : countChangeNote(sessions, earlierSessions, ""),
                earlierSessions == null ? "" : earlierSessions + " the week before", ProgressFactsBuilder.CARD_TRAINING));

        Integer minutes = whole(hero.path("minutes").path("value"));
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

    private static void addFood(List<ReadingFact> facts, JsonNode hero) {
        Integer logged = whole(hero.path("daysLogged").path("value"));
        if (logged == null || logged <= 0) {
            return;
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
