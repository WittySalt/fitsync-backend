package com.fitsync.nutrition.service;

import com.fitsync.nutrition.dto.RecipeStepTimerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                    + "|knead|marinat|toast|brown|sear|poach|braise|broil)\\w*", FLAGS);
    private static final Pattern EACH_SIDE = Pattern.compile(
            "\\beach side\\b|\\bper side\\b|\\bon both sides\\b|\\beach\\b[^.!?]*\\bside\\b", FLAGS);
    /** "until crispy on both sides" says when the food is done, not that it is turned halfway. */
    private static final Pattern DONENESS_BOTH_SIDES = Pattern.compile(
            "\\b(?:until|till)\\b[^,;]*\\bon both sides\\b", FLAGS);
    private static final Pattern ING_FORM = Pattern.compile(".+ing", FLAGS);
    private static final int LABEL_MAX = 32;
    private static final int LABEL_PHRASE_WORDS = 4;
    private static final Set<String> LABEL_STOP_WORDS = Set.of("in", "until", "till", "for", "over", "on", "at", "with",
            "into", "to", "about", "and", "then", "or", "from", "by", "while", "so");
    private static final Pattern LEADING_QUANTITY = Pattern.compile(
            "^(?:\\d+(?:[./]\\d+)?\\s?" + FRACTION + "?|" + FRACTION + ")(?:\\s?(?:g|kg|mg|ml|l|oz|lb|lbs)\\b)?\\s*",
            FLAGS);

    private static final Pattern OF_QUANTITY = Pattern.compile("\\bof\\s+(?=(?:\\d|" + FRACTION + "))", FLAGS);

    private static final Pattern MEASURE_WORDS = Pattern.compile(
            "g|kg|mg|ml|l|cl|dl|oz|ounces?|lbs?|pounds?|pints?|quarts?|cups?|tbsp?|tsp|tblsp|tablespoons?|teaspoons?|pinch(?:es)?|dash|handful"
                    + "|bunch|cloves?|cans?|tins?|pack(?:et)?|slices?|pieces?|sticks?|sprigs?|large|medium|small|of",
            FLAGS);

    /** Words that say how an ingredient is prepared; they are not part of its name. */
    private static final String PREPARATION_WORDS = "chopped|finely|roughly|coarsely|thinly|thickly|diced|sliced|peeled"
            + "|crushed|minced|grated|ground|fresh|freshly|beaten|softened|melted|cubed|shredded|toasted|cooked|drained"
            + "|rinsed|halved|quartered|trimmed|torn|mashed|pitted|deseeded|seeded|dried|chilled|sifted|crumbled"
            + "|lightly|julienned|skinned|boned|boneless|skinless";
    /** Phrases that say how an ingredient is used or how much of it is meant; they are not part of its name. */
    private static final String USE_PHRASES = "to serve|to garnish|for garnish|for the garnish|for frying|for serving"
            + "|to taste|to fry|to drizzle|a handful of|a pinch of|a splash of|a dash of|handful of|pinch of"
            + "|large handful|small handful|handfull|handful|splash|drizzle|sprinkling|spinkling|topping|garnish"
            + "|to decorate|for decoration|for dusting|for brushing|for greasing|for sprinkling";
    private static final String NOISE = "(?:" + USE_PHRASES + "|" + PREPARATION_WORDS + ")";
    private static final String SEPARATOR = "[\\s,;:\\-]";
    private static final Pattern LEADING_NOISE = Pattern.compile(
            "^(?:" + NOISE + "(?=" + SEPARATOR + "|$)" + SEPARATOR + "*)+", FLAGS);
    private static final Pattern TRAILING_NOISE = Pattern.compile(
            "(?:" + SEPARATOR + "+" + NOISE + ")+" + SEPARATOR + "*$", FLAGS);

    private RecipeStepAnalyzer() {
    }

    /**
     * The short ingredient name of a written line: "400g Salmon" gives "Salmon", "zest and juice of 1 Lime" gives
     * "Lime". Empty when nothing is left after the measure.
     */
    public static String ingredientName(String displayText) {
        if (displayText == null) {
            return "";
        }
        String text = displayText.strip();
        Matcher counted = OF_QUANTITY.matcher(text);
        int cut = -1;
        while (counted.find()) {
            cut = counted.end();
        }
        String name = IngredientMatcher.wordOf(cut < 0 ? text : text.substring(cut)).strip();
        // A preparation or use word can sit in front of a measure or behind another one, so the two cleanups
        // repeat until the text no longer changes.
        String previous;
        do {
            previous = name;
            String withoutLeading = LEADING_NOISE.matcher(name).replaceFirst("").strip();
            String withoutTrailing = TRAILING_NOISE.matcher(withoutLeading).replaceFirst("").strip();
            name = IngredientMatcher.wordOf(withoutTrailing).strip();
            if (name.isEmpty()) {
                // Nothing but noise words was written, so the line keeps its text rather than going blank.
                return previous;
            }
        } while (!name.equals(previous));
        return name;
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

    /** The durations of 30 seconds to 3 hours written in cooking sentences of the step, labelled as in step 1. */
    public static List<RecipeStepTimerView> timers(String text) {
        return timers(text, 1, null);
    }

    /**
     * The durations of 30 seconds to 3 hours written in cooking sentences of the step. Each gets a short label of at
     * most 32 characters; {@code mainIngredient} is the short name of the step's first ingredient, or null.
     */
    public static List<RecipeStepTimerView> timers(String text, int stepNumber, String mainIngredient) {
        List<RecipeStepTimerView> timers = new ArrayList<>();
        if (text == null) {
            return timers;
        }
        int from = 0;
        Matcher end = SENTENCE_END.matcher(text);
        while (end.find()) {
            addTimers(text.substring(from, end.start()), timers, text, stepNumber, mainIngredient);
            from = end.end();
        }
        addTimers(text.substring(from), timers, text, stepNumber, mainIngredient);
        return numbered(timers);
    }

    private static void addTimers(String sentence, List<RecipeStepTimerView> timers, String stepText, int stepNumber,
                                  String mainIngredient) {
        if (!COOKING_VERB.matcher(sentence).find()) {
            return;
        }
        boolean eachSide = EACH_SIDE.matcher(DONENESS_BOTH_SIDES.matcher(sentence).replaceAll("")).find();
        Matcher match = DURATION.matcher(sentence);
        while (match.find()) {
            int[] range = secondsOf(match);
            if (range != null && range[0] >= MIN_TIMER_SECONDS && range[1] <= MAX_TIMER_SECONDS) {
                String label = label(sentence, match.start(), stepText, stepNumber, mainIngredient);
                timers.add(new RecipeStepTimerView(range[0], range[1], eachSide, match.group().strip(), label));
            }
        }
    }

    /** A second timer with the same label as an earlier one in the step gets " · 2", a third " · 3". */
    private static List<RecipeStepTimerView> numbered(List<RecipeStepTimerView> timers) {
        List<RecipeStepTimerView> result = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (RecipeStepTimerView timer : timers) {
            int count = seen.merge(timer.label(), 1, Integer::sum);
            if (count == 1) {
                result.add(timer);
            } else {
                String suffix = " · " + count;
                String label = cap(timer.label(), LABEL_MAX - suffix.length()) + suffix;
                result.add(new RecipeStepTimerView(timer.seconds(), timer.maxSeconds(), timer.eachSide(),
                        timer.phrase(), label));
            }
        }
        return result;
    }

    /**
     * What the timer times: the cooking verb and the words after it up to a preposition, a comma or four words;
     * the verb and the step's main ingredient when nothing follows; then the step title; then "Step N timer".
     */
    private static String label(String sentence, int durationStart, String stepText, int stepNumber,
                                 String mainIngredient) {
        int begin = 0;
        int colon = sentence.indexOf(':');
        if (colon >= 0 && colon < durationStart
                && COOKING_VERB.matcher(sentence.substring(colon + 1, durationStart)).find()) {
            begin = colon + 1;
        }
        Matcher verbs = COOKING_VERB.matcher(sentence.substring(begin, durationStart));
        int verbStart = -1;
        int verbEnd = -1;
        boolean nearestIsParticiple = true;
        while (verbs.find()) {
            // The nearest verb before the time wins, but "stirring" or "turning" does not replace an earlier verb.
            boolean participle = ING_FORM.matcher(verbs.group()).matches();
            if (verbStart < 0 || !participle || nearestIsParticiple) {
                verbStart = begin + verbs.start();
                verbEnd = begin + verbs.end();
                nearestIsParticiple = participle;
            }
        }
        if (verbStart < 0) {
            Matcher any = COOKING_VERB.matcher(sentence);
            if (any.find()) {
                verbStart = any.start();
                verbEnd = any.end();
            }
        }
        if (verbStart >= 0) {
            String word = capitalise(sentence.substring(verbStart, verbEnd));
            String phrase = phraseAfter(sentence.substring(verbEnd, Math.max(verbEnd, durationStart)));
            String label;
            if (!phrase.isEmpty()) {
                label = word + " " + phrase;
            } else if (mainIngredient != null && !mainIngredient.isBlank()) {
                label = word + " · " + mainIngredient.strip();
            } else {
                label = word;
            }
            label = cap(label, LABEL_MAX);
            if (!label.isEmpty()) {
                return label;
            }
        }
        String title = cap(title(stepText, stepNumber), LABEL_MAX);
        return title.isEmpty() ? "Step " + stepNumber + " timer" : title;
    }

    private static String phraseAfter(String rest) {
        List<String> words = new ArrayList<>();
        for (String raw : rest.strip().split("\\s+")) {
            if (raw.isEmpty() || words.size() >= LABEL_PHRASE_WORDS) {
                break;
            }
            String word = raw.replaceAll("[,;:()]+$", "");
            if (word.isEmpty() || raw.startsWith("(") || LABEL_STOP_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                break;
            }
            words.add(word);
            if (raw.length() != word.length()) {
                break;
            }
        }
        return String.join(" ", words);
    }

    private static String capitalise(String word) {
        return word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    /** The text cut to at most {@code max} characters on a word boundary. */
    private static String cap(String text, int max) {
        String clean = text == null ? "" : text.strip();
        if (clean.length() <= max) {
            return clean;
        }
        int space = clean.lastIndexOf(' ', max);
        String cut = space > 0 ? clean.substring(0, space) : clean.substring(0, max);
        return cut.replaceAll("[\\s·,;:-]+$", "");
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
