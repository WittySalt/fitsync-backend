package com.fitsync.recommendation.coach.reading;

import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.cite;
import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.find;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Writes the progress page's default reading from fixed templates, never a model call.
 *
 * <p>The direction of the weight comes from the rate fact's note and the comparison of the two dates
 * from the insight itself, so a headline that says the goal is reached before or after its date is
 * decided by the dates and not by wording. Every candidate goes through {@link ReadingValidator}.
 */
@Component
public class ProgressRuleReadingWriter {

    private static final String DOWN = "Down";
    private static final String UP = "Up";
    private static final String UNCHANGED = "Unchanged";

    private final ReadingValidator validator;

    public ProgressRuleReadingWriter(ReadingValidator validator) {
        this.validator = validator;
    }

    public Optional<RuleReadingWriter.RuleReading> write(ProgressInsight insight, List<ReadingFact> facts) {
        ReadingFact weight = find(facts, "WEIGHT");
        ReadingFact rate = find(facts, "WEEKLY_RATE");
        ReadingFact goal = find(facts, "GOAL");
        ReadingFact calories = find(facts, "CALORIES_PER_DAY");

        List<String> sentences = new ArrayList<>();
        if (weight != null) {
            sentences.add(weightSentence(weight));
        }
        if (rate != null) {
            sentences.add(rateSentence(rate));
        }
        if (goal != null) {
            sentences.add(goalSentence(goal, insight));
        }
        if (calories != null) {
            sentences.add("Calories came to " + calories.value() + " kcal a day " + cite(calories) + ".");
        }
        if (sentences.isEmpty()) {
            return Optional.empty();
        }
        return RuleReadingCandidates.firstValid(validator, headline(insight, rate, goal), sentences, facts);
    }

    private static String headline(ProgressInsight insight, ReadingFact rate, ReadingFact goal) {
        String direction = directionOf(rate);
        if (goal != null && insight.projectedDate() != null) {
            String reach = "At this rate you reach " + goal.value() + " kg";
            if (insight.goalDate() == null) {
                return reach + ".";
            }
            return reach + (insight.projectedDate().isAfter(insight.goalDate())
                    ? " after your date."
                    : " before your date.");
        }
        if (goal != null && UNCHANGED.equals(direction)) {
            return "Your weight has held over four weeks.";
        }
        if (goal != null && movingAwayFromGoal(insight, direction)) {
            return "Your weight is moving away from " + goal.value() + " kg.";
        }
        return switch (direction) {
            case DOWN -> "Your weight went down over four weeks.";
            case UP -> "Your weight went up over four weeks.";
            case UNCHANGED -> "Your weight held over four weeks.";
            default -> "Your latest weight.";
        };
    }

    private static boolean movingAwayFromGoal(ProgressInsight insight, String direction) {
        if (insight.weightKg() == null || insight.goalWeightKg() == null) {
            return false;
        }
        int goalComparedWithWeight = insight.goalWeightKg().compareTo(insight.weightKg());
        return (UP.equals(direction) && goalComparedWithWeight < 0)
                || (DOWN.equals(direction) && goalComparedWithWeight > 0);
    }

    /** The rate fact's note as a direction word, or an empty string when there is no rate. */
    private static String directionOf(ReadingFact rate) {
        if (rate == null || rate.note() == null) {
            return "";
        }
        if (rate.note().startsWith(DOWN)) {
            return DOWN;
        }
        if (rate.note().startsWith(UP)) {
            return UP;
        }
        return UNCHANGED.equals(rate.note()) ? UNCHANGED : "";
    }

    private static String weightSentence(ReadingFact weight) {
        String on = weight.note() == null || weight.note().isEmpty() ? "" : " on " + weight.note();
        return "You weighed " + weight.value() + " kg" + on + " " + cite(weight) + ".";
    }

    private static String rateSentence(ReadingFact rate) {
        String direction = directionOf(rate);
        if (DOWN.equals(direction)) {
            return "That is down " + rate.value() + " kg a week " + cite(rate) + ".";
        }
        if (UP.equals(direction)) {
            return "That is up " + rate.value() + " kg a week " + cite(rate) + ".";
        }
        return "It stayed the same over four weeks " + cite(rate) + ".";
    }

    private static String goalSentence(ReadingFact goal, ProgressInsight insight) {
        String by = goal.note() == null || goal.note().isEmpty() ? "" : " " + goal.note();
        String sentence = "Your goal is " + goal.value() + " kg" + by + " " + cite(goal);
        if (insight.projectedDate() != null) {
            sentence += ", and this rate gets there on "
                    + ProgressFactsBuilder.dateFor(insight.projectedDate(), insight);
        }
        return sentence + ".";
    }
}
