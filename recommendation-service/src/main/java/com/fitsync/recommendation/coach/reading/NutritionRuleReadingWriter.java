package com.fitsync.recommendation.coach.reading;

import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.cite;
import static com.fitsync.recommendation.coach.reading.RuleReadingCandidates.find;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Writes the nutrition page's default reading from fixed templates, never a model call.
 *
 * <p>It follows the same rule as {@link RuleReadingWriter}: a sentence can only say what a fact's
 * note states, and every candidate is checked by {@link ReadingValidator} before it is returned.
 * The headline is chosen from the meal shares the facts already hold, so it never repeats a
 * calculation of its own.
 */
@Component
public class NutritionRuleReadingWriter {

    /** The share of the day's protein at lunch and dinner from which the headline names them. */
    private static final int LUNCH_DINNER_HEADLINE_SHARE = 60;

    /** The share at breakfast from which the headline says the day started early. */
    private static final int BREAKFAST_HEADLINE_SHARE = 35;

    private static final Pattern SHARE = Pattern.compile("^(\\d+)%");

    private final ReadingValidator validator;

    public NutritionRuleReadingWriter(ReadingValidator validator) {
        this.validator = validator;
    }

    public Optional<RuleReadingWriter.RuleReading> write(NutritionInsight insight, List<ReadingFact> facts) {
        ReadingFact protein = find(facts, "PROTEIN_PER_DAY");
        ReadingFact breakfast = find(facts, "PROTEIN_BREAKFAST");
        ReadingFact lunchDinner = find(facts, "PROTEIN_LUNCH_DINNER");
        ReadingFact calories = find(facts, "CALORIES_PER_DAY");

        List<String> sentences = new ArrayList<>();
        if (protein != null) {
            sentences.add(proteinSentence(protein, insight));
        }
        String meals = mealSentence(breakfast, lunchDinner, protein != null);
        if (meals != null) {
            sentences.add(meals);
        }
        if (calories != null) {
            sentences.add("Calories came to " + calories.value() + " kcal a day " + cite(calories) + ".");
        }
        if (sentences.isEmpty()) {
            return Optional.empty();
        }
        boolean aboutProtein = protein != null || breakfast != null || lunchDinner != null;
        return RuleReadingCandidates.firstValid(validator, headline(breakfast, lunchDinner, aboutProtein),
                sentences, facts);
    }

    private static String headline(ReadingFact breakfast, ReadingFact lunchDinner, boolean aboutProtein) {
        int lunchDinnerShare = shareOf(lunchDinner);
        int breakfastShare = shareOf(breakfast);
        if (lunchDinnerShare >= LUNCH_DINNER_HEADLINE_SHARE) {
            return "Most of your protein came at lunch and dinner.";
        }
        if (breakfastShare >= BREAKFAST_HEADLINE_SHARE) {
            return "Your protein started early in the day.";
        }
        if (lunchDinnerShare >= 0 || breakfastShare >= 0) {
            return "Your protein was spread across the day.";
        }
        return aboutProtein ? "Your protein for the week." : "Your calories for the week.";
    }

    /** The whole-percent share a meal fact states, or -1 when the fact is missing or states none. */
    private static int shareOf(ReadingFact fact) {
        if (fact == null || fact.note() == null) {
            return -1;
        }
        Matcher matcher = SHARE.matcher(fact.note());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String proteinSentence(ReadingFact protein, NutritionInsight insight) {
        String base = "You ate " + protein.value() + " g of protein a day " + cite(protein);
        String note = protein.note() == null ? "" : protein.note();
        if (insight.earlierProteinPerDayGrams() == null) {
            return base + ".";
        }
        String earlier = ReadingFactsBuilder.upToOneDecimal(insight.earlierProteinPerDayGrams());
        if (note.startsWith("Up")) {
            return base + ", up from " + earlier + " g.";
        }
        if (note.startsWith("Down")) {
            return base + ", down from " + earlier + " g.";
        }
        if ("Unchanged".equals(note)) {
            return base + ", the same as the week before.";
        }
        return base + ".";
    }

    private static String mealSentence(ReadingFact breakfast, ReadingFact lunchDinner, boolean hasProtein) {
        // "of it" only reads properly when the sentence before it named the day's protein.
        String of = hasProtein ? " of it " : " of protein a day ";
        if (breakfast != null && lunchDinner != null) {
            return "Breakfast gave " + breakfast.value() + " g" + of + cite(breakfast)
                    + ", and lunch and dinner gave " + lunchDinner.value() + " g " + cite(lunchDinner) + ".";
        }
        if (breakfast != null) {
            return "Breakfast gave " + breakfast.value() + " g" + of + cite(breakfast) + ".";
        }
        if (lunchDinner != null) {
            return "Lunch and dinner gave " + lunchDinner.value() + " g" + of + cite(lunchDinner) + ".";
        }
        return null;
    }
}
