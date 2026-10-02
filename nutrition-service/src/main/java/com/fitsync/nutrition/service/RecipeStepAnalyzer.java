package com.fitsync.nutrition.service;

import com.fitsync.nutrition.dto.RecipeStepTimerView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the cook mode fields of a recipe step from its text: a short title, the ingredients the step uses
 * and the durations written in it. Pure rules, no model and no storage, so it runs whenever a detail is read.
 */
public final class RecipeStepAnalyzer {

    /** An ingredient as the matcher needs it. */
    public record IngredientRef(UUID id, String foodName, String displayText) {
    }

    private static final int TITLE_MAX = 60;
    private static final int TITLE_CUT = 57;
    private static final int MIN_TIMER_SECONDS = 30;
    private static final int MAX_TIMER_SECONDS = 3 * 3600;
    private static final int MAX_FOOD_NAME_WORDS = 3;
    private static final int MIN_WORD_LENGTH = 3;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?:\\s+|$)|\\R+");
    private static final String[] TITLE_CUTS = {", ", " and ", " then "};

    private static final String FRACTION = "[½¼¾]";
    private static final String NUM = "(?:\\d+(?:\\.\\d+)?\\s?" + FRACTION + "?|" + FRACTION + ")";
    private static final String HOURS = "(?:hours?|hrs?)";
    private static final String MINUTES = "(?:minutes?|mins?)";
    private static final String SECONDS = "(?:seconds?|secs?)";
    private static final Pattern DURATION = Pattern.compile(
            "(?<![\\p{L}\\d.])(?:"
                    + "(?<ch>" + NUM + ")\\s*" + HOURS + "\\s*(?:and\\s+)?(?<cm>" + NUM + ")\\s*" + MINUTES + "\\b"
                    + "|(?<half>half an hour)"
                    + "|(?<lo>" + NUM + ")(?:\\s*(?:-|–|to)\\s*(?<hi>" + NUM + "))?\\s*"
                    + "(?<unit>" + HOURS + "|" + MINUTES + "|" + SECONDS + ")\\b)",
            FLAGS);
    private static final Pattern COOKING_VERB = Pattern.compile(
            "\\b(?:cook|fry|fried|frying|bake|baked|baking|roast|simmer|boil|grill|rest|chill|steam|saut|stir|blend"
                    + "|knead|marinat|toast)\\w*", FLAGS);
    private static final Pattern EACH_SIDE = Pattern.compile(
            "\\beach side\\b|\\bper side\\b|\\bon both sides\\b|\\beach\\b[^.!?]*\\bside\\b", FLAGS);
    private static final Pattern LEADING_QUANTITY = Pattern.compile(
            "^(?:\\d+(?:[./]\\d+)?\\s?" + FRACTION + "?|" + FRACTION + ")(?:\\s?(?:g|kg|mg|ml|l|oz|lb|lbs)\\b)?\\s*",
            FLAGS);

    private static final Pattern MEASURE_WORDS = Pattern.compile(
            "g|kg|mg|ml|l|cl|dl|oz|lbs?|cups?|tbsp?|tsp|tblsp|tablespoons?|teaspoons?|pinch(?:es)?|dash|handful"
                    + "|bunch|cloves?|cans?|tins?|pack(?:et)?|slices?|pieces?|sticks?|sprigs?|large|medium|small|of",
            FLAGS);

    private RecipeStepAnalyzer() {
    }

    /** The first sentence of the step, cut at a clause boundary to at most 60 characters, never empty. */
    public static String title(String text, int stepNumber) {
        String fallback = "Step " + stepNumber;
        if (text == null) {
            return fallback;
        }
        String trimmed = text.strip();
        Matcher end = SENTENCE_END.matcher(trimmed);
        String sentence = (end.find() ? trimmed.substring(0, end.start()) : trimmed).strip();
        if (sentence.isEmpty()) {
            return fallback;
        }
        if (sentence.length() <= TITLE_MAX) {
            return sentence;
        }
        String head = sentence.substring(0, TITLE_MAX);
        int cut = -1;
        for (String boundary : TITLE_CUTS) {
            cut = Math.max(cut, head.lastIndexOf(boundary));
        }
        if (cut > 0) {
            String clause = head.substring(0, cut).replaceAll("[,;\s]+$", "");
            if (!clause.isEmpty()) {
                return clause;
            }
        }
        int space = sentence.lastIndexOf(' ', TITLE_CUT);
        String words = (space > 0 ? sentence.substring(0, space) : sentence.substring(0, TITLE_CUT)).strip();
        return words.isEmpty() ? fallback : words + "…";
    }

    /** The ingredient ids each step uses; an ingredient belongs to the first step that mentions it. */
    public static List<List<UUID>> ingredientIdsByStep(List<String> stepTexts, List<IngredientRef> ingredients) {
        List<IngredientMatcher> pending = new ArrayList<>();
        for (IngredientRef ref : ingredients) {
            IngredientMatcher matcher = IngredientMatcher.of(ref);
            if (matcher != null) {
                pending.add(matcher);
            }
        }
        List<List<UUID>> result = new ArrayList<>();
        for (String text : stepTexts) {
            List<UUID> used = new ArrayList<>();
            if (text != null) {
                Iterator<IngredientMatcher> candidates = pending.iterator();
                while (candidates.hasNext()) {
                    IngredientMatcher candidate = candidates.next();
                    if (candidate.pattern.matcher(text).find()) {
                        used.add(candidate.id);
                        candidates.remove();
                    }
                }
            }
            result.add(List.copyOf(used));
        }
        return result;
    }

    /** The durations of 30 seconds to 3 hours written in cooking sentences of the step. */
    public static List<RecipeStepTimerView> timers(String text) {
        List<RecipeStepTimerView> timers = new ArrayList<>();
        if (text == null) {
            return timers;
        }
        int from = 0;
        Matcher end = SENTENCE_END.matcher(text);
        while (end.find()) {
            addTimers(text.substring(from, end.start()), timers);
            from = end.end();
        }
        addTimers(text.substring(from), timers);
        return timers;
    }

    private static void addTimers(String sentence, List<RecipeStepTimerView> timers) {
        if (!COOKING_VERB.matcher(sentence).find()) {
            return;
        }
        boolean eachSide = EACH_SIDE.matcher(sentence).find();
        Matcher match = DURATION.matcher(sentence);
        while (match.find()) {
            int[] range = secondsOf(match);
            if (range != null && range[0] >= MIN_TIMER_SECONDS && range[1] <= MAX_TIMER_SECONDS) {
                timers.add(new RecipeStepTimerView(range[0], range[1], eachSide, match.group().strip()));
            }
        }
    }

    private static int[] secondsOf(Matcher match) {
        if (match.group("half") != null) {
            return new int[] {1800, 1800};
        }
        if (match.group("ch") != null) {
            int total = (int) Math.round(number(match.group("ch")) * 3600 + number(match.group("cm")) * 60);
            return new int[] {total, total};
        }
        String unit = match.group("unit").toLowerCase(Locale.ROOT);
        double factor = unit.startsWith("h") ? 3600 : unit.startsWith("m") ? 60 : 1;
        int low = (int) Math.round(number(match.group("lo")) * factor);
        int high = match.group("hi") == null ? low : (int) Math.round(number(match.group("hi")) * factor);
        return high < low ? null : new int[] {low, high};
    }

    private static double number(String raw) {
        String text = raw.strip();
        double fraction = 0;
        if (!text.isEmpty()) {
            char last = text.charAt(text.length() - 1);
            if (last == '½' || last == '¼' || last == '¾') {
                fraction = last == '½' ? 0.5 : last == '¼' ? 0.25 : 0.75;
                text = text.substring(0, text.length() - 1).strip();
            }
        }
        return (text.isEmpty() ? 0 : Double.parseDouble(text)) + fraction;
    }

    /** Whole-word matcher for one ingredient: its words, simple plurals, and the last word of a longer name. */
    private static final class IngredientMatcher {
        private final UUID id;
        private final Pattern pattern;

        private IngredientMatcher(UUID id, Pattern pattern) {
            this.id = id;
            this.pattern = pattern;
        }

        static IngredientMatcher of(IngredientRef ref) {
            Set<String> phrases = new LinkedHashSet<>();
            addPhrase(phrases, wordOf(ref.displayText()));
            String name = ref.foodName() == null ? "" : ref.foodName().strip();
            if (!name.isEmpty() && name.indexOf(',') < 0 && name.split("\\s+").length <= MAX_FOOD_NAME_WORDS) {
                addPhrase(phrases, name);
            }
            if (phrases.isEmpty()) {
                return null;
            }
            Set<String> alternatives = new LinkedHashSet<>();
            for (String phrase : phrases) {
                alternatives.add(alternative(phrase));
                int space = phrase.lastIndexOf(' ');
                if (space > 0 && phrase.length() - space - 1 >= MIN_WORD_LENGTH) {
                    alternatives.add(alternative(phrase.substring(space + 1)));
                }
            }
            Pattern pattern = Pattern.compile(
                    "(?<![\\p{L}\\d])(?:" + String.join("|", alternatives) + ")(?![\\p{L}\\d])", FLAGS);
            return new IngredientMatcher(ref.id(), pattern);
        }

        private static void addPhrase(Set<String> phrases, String phrase) {
            String clean = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\d' -]", " ")
                    .replaceAll("\\s+", " ").strip();
            if (clean.length() >= MIN_WORD_LENGTH) {
                phrases.add(clean);
            }
        }

        /** The text after the measure: "400g Salmon" gives "Salmon", "3 tablespoons Olive oil" gives "Olive oil". */
        private static String wordOf(String displayText) {
            if (displayText == null) {
                return "";
            }
            String rest = displayText.replaceAll("\\(.*?\\)", " ").strip();
            boolean stripped = true;
            while (stripped && !rest.isEmpty()) {
                stripped = false;
                Matcher quantity = LEADING_QUANTITY.matcher(rest);
                if (quantity.find() && quantity.end() > 0) {
                    rest = rest.substring(quantity.end());
                    stripped = true;
                    continue;
                }
                int space = rest.indexOf(' ');
                String first = (space < 0 ? rest : rest.substring(0, space)).replaceAll("[^\\p{L}]", "");
                if (space > 0 && MEASURE_WORDS.matcher(first).matches()) {
                    rest = rest.substring(space + 1).strip();
                    stripped = true;
                }
            }
            return rest;
        }

        /** The phrase as a regex where the last word also matches its simple plural or singular. */
        private static String alternative(String phrase) {
            int space = phrase.lastIndexOf(' ');
            String head = space < 0 ? "" : Pattern.quote(phrase.substring(0, space + 1));
            String last = space < 0 ? phrase : phrase.substring(space + 1);
            StringBuilder forms = new StringBuilder(Pattern.quote(last));
            if (last.endsWith("ies") && last.length() > MIN_WORD_LENGTH + 1) {
                forms.append('|').append(Pattern.quote(last.substring(0, last.length() - 3) + "y"));
            } else if (last.endsWith("es") && last.length() > MIN_WORD_LENGTH + 1) {
                forms.append('|').append(Pattern.quote(last.substring(0, last.length() - 2)))
                        .append('|').append(Pattern.quote(last.substring(0, last.length() - 1)));
            } else if (last.endsWith("s") && last.length() > MIN_WORD_LENGTH) {
                forms.append('|').append(Pattern.quote(last.substring(0, last.length() - 1)));
            } else {
                forms.append('|').append(Pattern.quote(last + "s")).append('|').append(Pattern.quote(last + "es"));
                if (last.endsWith("y") && last.length() > 2) {
                    forms.append('|').append(Pattern.quote(last.substring(0, last.length() - 1) + "ies"));
                }
            }
            return head + "(?:" + forms + ")";
        }
    }
}
