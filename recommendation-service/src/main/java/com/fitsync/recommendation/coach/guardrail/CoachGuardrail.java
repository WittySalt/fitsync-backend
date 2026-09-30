package com.fitsync.recommendation.coach.guardrail;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitsync.recommendation.service.rules.NutritionGapRule;
import org.springframework.stereotype.Component;

/**
 * The checks that run before any model sees a message.
 *
 * <p>A guardrail is a rule, not a prompt. The system prompt already tells the model never to
 * suggest eating below the floor, and a well-behaved model mostly obeys it; but "mostly" is the
 * wrong standard for the one answer that could hurt someone, and a rule that runs first cannot be
 * talked out of it by a cleverly phrased question. It also costs nothing: no tool, no inference,
 * an answer in under a millisecond, recorded on the turn as a refusal rather than an answer so the
 * evaluation harness can count it.
 *
 * <p>Deliberately narrow. One rule, for the one line the product has already drawn elsewhere
 * (the nutrition gap rule's daily minimum), phrased to the user with the number and why. A wider
 * net would start refusing ordinary questions, and a coach that refuses ordinary questions gets
 * ignored, which defeats the guardrail that matters.
 */
@Component
public class CoachGuardrail {

    /** The same floor the rule engine uses, so the coach and the cards never disagree about it. */
    public static final int CALORIE_FLOOR = NutritionGapRule.MIN_DAILY_CALORIES;

    /**
     * A number of calories, however the user wrote it: 800 kcal, 800 calories, 800cal, 800 cals, or
     * with a thousands comma as 2,173 kcal. The comma form is read whole, because reading only the
     * digits after the comma turned a normal intake of 2,173 kcal into 173 and refused it.
     */
    private static final Pattern CALORIE_FIGURE = Pattern.compile(
            "(?<![\\d,])(\\d{1,3}(?:,\\d{3}){1,2}|\\d{2,5})\\s*(?:k?cal(?:orie)?s?)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Words that make a calorie figure a daily intake the user means to live on.
     *
     * <p>Narrower than "any mention of eating", and for a reason found by the evaluation harness:
     * "what should I eat tonight with 63 kcal left" was refused on its first run, because 63 is
     * below the floor and "eat" read as intent. A remaining budget is not a plan. The verbs and
     * phrases here are the ones that say how much per day.
     */
    private static final Pattern INTAKE_INTENT = Pattern.compile(
            "\\b(a day|per day|daily|each day|every day|diet|fast|fasting|deficit|cut to|cutting to|drop to|dropping to|"
                    + "go down to|stick to|limit|restrict|only)\\b",
            Pattern.CASE_INSENSITIVE);

    /** A figure described as what is left, burned or in one meal is not a daily plan, whatever else the sentence says. */
    private static final Pattern NOT_A_PLAN = Pattern.compile(
            "\\b(left|remaining|burn|burned|burnt|for (?:breakfast|lunch|dinner|a snack)|one (?:meal|burger|serving)|per (?:meal|serving))\\b",
            Pattern.CASE_INSENSITIVE);

    /** @return the refusal to send instead of asking the model, or empty when the message is fine */
    public Optional<Refusal> check(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);
        Matcher figures = CALORIE_FIGURE.matcher(lower);
        while (figures.find()) {
            int calories = Integer.parseInt(figures.group(1).replace(",", ""));
            if (calories < CALORIE_FLOOR
                    && INTAKE_INTENT.matcher(lower).find()
                    && !NOT_A_PLAN.matcher(lower).find()) {
                return Optional.of(new Refusal(
                        "CALORIE_FLOOR",
                        "I won't plan for " + calories + " kcal a day. " + CALORIE_FLOOR
                                + " kcal is the lowest daily intake this coach will work with, because "
                                + "below it you cannot get enough protein and micronutrients to train "
                                + "safely. A moderate deficit from your computed target reaches a weight "
                                + "goal without that risk, and a dietitian is the right person for anything "
                                + "more aggressive."));
            }
        }
        return Optional.empty();
    }

    /**
     * @param rule    which guardrail fired, recorded on the turn as the verdict
     * @param message what the user is told, complete in itself and addressed to them
     */
    public record Refusal(String rule, String message) {
    }
}
