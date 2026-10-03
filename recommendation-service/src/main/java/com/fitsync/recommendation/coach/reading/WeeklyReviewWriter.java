package com.fitsync.recommendation.coach.reading;

import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.cite;
import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.find;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Writes a weekly review from fixed templates, never a model call, the same way the page readings'
 * rule writers do. A model review only ever replaces it once one has passed the same check.
 *
 * <p>The headline holds no number, and the direction it names comes from a fact's note, so the
 * validator can hold it to the facts. Every candidate goes through {@link ReadingValidator}.
 */
@Component
public class WeeklyReviewWriter {

    private final ReadingValidator validator;

    public WeeklyReviewWriter(ReadingValidator validator) {
        this.validator = validator;
    }

    /**
     * @param currentWeek true when the week is still going, so the headline says "so far"
     */
    public Optional<RuleReadingWriter.RuleReading> write(List<ReadingFact> facts, boolean currentWeek) {
        ReadingFact sessions = find(facts, "SESSIONS");
        ReadingFact minutes = find(facts, "MINUTES");
        ReadingFact records = find(facts, "RECORDS");
        ReadingFact calories = find(facts, "CALORIES_PER_DAY");
        ReadingFact protein = find(facts, "PROTEIN_PER_DAY");
        ReadingFact balance = find(facts, "BALANCE_PER_DAY");
        ReadingFact weight = find(facts, "WEIGHT_CHANGE");

        List<String> sentences = new ArrayList<>();
        if (sessions != null) {
            sentences.add("You trained " + sessions.value() + " " + sessions.unit()
                    + (minutes == null ? "" : " over " + minutes.value() + " minutes")
                    + " " + cite(sessions) + (minutes == null ? "" : " " + cite(minutes)) + ".");
        }
        if (records != null) {
            sentences.add("You set " + records.value() + ("1".equals(records.value()) ? " record " : " records ")
                    + cite(records) + ".");
        }
        if (calories != null && protein != null) {
            sentences.add("You ate " + calories.value() + " kcal and " + protein.value()
                    + " g of protein a day on logged days " + cite(calories) + " " + cite(protein) + ".");
        } else if (calories != null) {
            sentences.add("You ate " + calories.value() + " kcal a day on logged days " + cite(calories) + ".");
        } else if (protein != null) {
            sentences.add("You ate " + protein.value() + " g of protein a day on logged days " + cite(protein) + ".");
        }
        if (balance != null) {
            sentences.add(balanceSentence(balance));
        }
        if (weight != null) {
            sentences.add(weightSentence(weight));
        }
        if (sentences.isEmpty()) {
            return Optional.empty();
        }
        return RuleReadingCandidates.firstValid(validator, headline(currentWeek, sessions, records), sentences, facts);
    }

    private static String headline(boolean currentWeek, ReadingFact sessions, ReadingFact records) {
        if (currentWeek) {
            return "Your week so far.";
        }
        if (records != null) {
            return "1".equals(records.value()) ? "A week with a new record." : "A week with new records.";
        }
        String note = sessions == null || sessions.note() == null ? "" : sessions.note();
        if (note.startsWith("Up")) {
            return "More training than the week before.";
        }
        if (note.startsWith("Down")) {
            return "Less training than the week before.";
        }
        return "Your week in review.";
    }

    private static String balanceSentence(ReadingFact balance) {
        return switch (balance.note()) {
            case "Under" -> "That is " + balance.value() + " kcal a day under the energy you used " + cite(balance) + ".";
            case "Over" -> "That is " + balance.value() + " kcal a day over the energy you used " + cite(balance) + ".";
            default -> "What you ate matched the energy you used " + cite(balance) + ".";
        };
    }

    private static String weightSentence(ReadingFact weight) {
        return switch (weight.note()) {
            case "Down" -> "Your weight went down " + weight.value() + " kg " + cite(weight) + ".";
            case "Up" -> "Your weight went up " + weight.value() + " kg " + cite(weight) + ".";
            default -> "Your weight stayed the same " + cite(weight) + ".";
        };
    }
}
