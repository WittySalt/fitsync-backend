package com.fitsync.recommendation.coach.orchestrator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitsync.recommendation.coach.guardrail.CoachVoice;
import org.springframework.stereotype.Component;

/**
 * Checks a chat answer the way {@link com.fitsync.recommendation.coach.reading.ReadingValidator}
 * checks a pinned reading: every number must be one the coach was actually given, the voice must
 * stay in the coach's register, and a citation of a pinned fact must point somewhere real and say
 * only what that fact showed.
 *
 * <p>This exists because the reading is checked and the chat is not, and testing turned up exactly
 * the two failures that gap allows: a figure attached to the wrong fact, and a per-session number
 * spoken of as a weekly one. Pure and deterministic, on purpose, for the same reason the reading
 * checker is: it has to run on every answer without adding a model call of its own.
 *
 * <p>Negation is not checked here. "That is not in your records" is a legitimate thing for the
 * coach to say in chat, where the reading never has to say it because it only ever describes what
 * a page already shows.
 */
@Component
public class AnswerValidator {

    private static final int MAX_ANSWER_LENGTH = 700;

    /** A citation marker such as {@code [2]}, removed from the answer before the number check. */
    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,2})]");

    /**
     * A number token: comma-grouped ({@code 1,923}) or a plain digit run ({@code 1923}), with an
     * optional decimal tail. A trailing {@code %} or an ordinal suffix such as {@code th} is left
     * as surrounding text, so {@code 12%} reads as 12 and {@code 25th} reads as 25. Applied to a
     * date such as {@code 2026-09-25} or a time such as {@code 18:30}, it finds each digit run
     * around the punctuation as its own number, which is exactly the three-numbers-for-a-date and
     * two-numbers-for-a-time behaviour this checker needs, with no special case required for either.
     */
    private static final Pattern NUMBER_TOKEN = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?");

    /** A sentence ends at a full stop that is followed by a space, so 22.5 stays whole. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.])\\s+");

    private static final Pattern WEEKLY_MEASURE = Pattern.compile(
            "(?i)\\b(this week|a week|weekly|per week|the week)\\b");

    private static final Pattern CURRENT_WEEK = Pattern.compile("(?i)\\bthis week\\b");

    /** A count of sessions or minutes, such as "4 workouts". A number after "of" is a target. */
    private static final Pattern COUNTED = Pattern.compile(
            "(?i)(?<!\\bof )(\\d{1,3}(?:,\\d{3})+|\\d+)\\s+(?:completed\\s+|total\\s+)?"
                    + "(workouts?|sessions?|minutes|min)\\b");

    /** Words that place a sentence in a week before the current one, or in a run of days. */
    private static final Pattern EARLIER_PERIOD = Pattern.compile(
            "(?i)\\b(last (?:full )?week|the week before|previous week|past week|"
                    + "(?:last|past) (?:7|seven) days)\\b");

    /** @return the checked result: valid with no violations, or invalid with every rule broken */
    public Result validate(String answer, String question, List<String> transcript, TurnContext turnContext) {
        List<String> violations = new ArrayList<>();

        if (answer == null || answer.isBlank()) {
            violations.add("ANSWER_BLANK");
            return new Result(false, violations);
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            violations.add("ANSWER_TOO_LONG");
        }

        String withoutCitations = CITATION_MARKER.matcher(answer).replaceAll(" ");
        Set<BigDecimal> givenNumbers = collectGivenNumbers(question, transcript, turnContext);
        for (String badNumber : numbersNotIn(withoutCitations, givenNumbers)) {
            violations.add("NUMBER_NOT_IN_RECORDS:" + badNumber);
        }

        violations.addAll(CoachVoice.toneViolations(answer));

        violations.addAll(weekNotNamedViolations(answer, WeekFigures.from(transcript)));

        TurnContext.PinnedReading pinnedReading = turnContext == null ? null : turnContext.pinnedReading();
        if (pinnedReading != null) {
            violations.addAll(citationViolations(answer, pinnedReading));
            violations.addAll(directionViolations(answer, pinnedReading));
            violations.addAll(measureMismatchViolations(answer, pinnedReading));
            violations.addAll(periodMismatchViolations(answer, pinnedReading));
        }

        return new Result(violations.isEmpty(), violations);
    }

    /**
     * Every number in {@code transcript} (tool observations, the pinned reading's facts as they were
     * placed there, prior turns) and in {@code question}, so a figure the model copied from either
     * passes, and one it worked out for itself does not. The pinned reading's facts are also read
     * directly off {@code turnContext} rather than relied on to still be phrased the same way inside
     * the transcript text, so this still holds if that phrasing ever changes.
     */
    private static Set<BigDecimal> collectGivenNumbers(String question, List<String> transcript,
                                                        TurnContext turnContext) {
        Set<BigDecimal> numbers = new LinkedHashSet<>();
        addNumbersFrom(numbers, question);
        if (transcript != null) {
            for (String entry : transcript) {
                addNumbersFrom(numbers, entry);
            }
        }
        if (turnContext != null && turnContext.pinnedReading() != null) {
            for (TurnContext.PinnedFact fact : turnContext.pinnedReading().facts()) {
                addNumbersFrom(numbers, fact.value());
                addNumbersFrom(numbers, fact.unit());
                addNumbersFrom(numbers, fact.note());
                addNumbersFrom(numbers, fact.detail());
            }
        }
        return numbers;
    }

    private static void addNumbersFrom(Set<BigDecimal> into, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = NUMBER_TOKEN.matcher(text);
        while (matcher.find()) {
            into.add(parseNumber(matcher.group()).stripTrailingZeros());
        }
    }

    private static List<String> numbersNotIn(String text, Set<BigDecimal> givenNumbers) {
        List<String> offending = new ArrayList<>();
        Matcher matcher = NUMBER_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            BigDecimal parsed = parseNumber(token).stripTrailingZeros();
            boolean given = givenNumbers.stream().anyMatch(candidate -> candidate.compareTo(parsed) == 0);
            if (!given) {
                offending.add(token);
            }
        }
        return offending;
    }

    private static BigDecimal parseNumber(String token) {
        return new BigDecimal(token.replace(",", ""));
    }

    /** Every {@code [n]} in the answer must name a fact the pinned reading actually has. */
    private static List<String> citationViolations(String answer, TurnContext.PinnedReading pinnedReading) {
        List<String> violations = new ArrayList<>();
        Set<Integer> factNumbers = new LinkedHashSet<>();
        for (TurnContext.PinnedFact fact : pinnedReading.facts()) {
            factNumbers.add(fact.number());
        }
        for (Integer citation : citationsIn(answer)) {
            if (!factNumbers.contains(citation)) {
                violations.add("INVALID_CITATION:" + citation);
            }
        }
        return violations;
    }

    /**
     * Checks each sentence that cites a pinned fact against only the facts it cites, the same rule
     * {@code ReadingValidator} applies to the reading itself. A sentence with no citation is not
     * checked at all: a chat answer routinely reports a tool result that carries no direction, and
     * that is not the mistake this rule exists to catch.
     */
    private static List<String> directionViolations(String answer, TurnContext.PinnedReading pinnedReading) {
        List<String> violations = new ArrayList<>();
        String[] sentences = SENTENCE_END.split(answer.strip());
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            List<TurnContext.PinnedFact> cited = factsCitedIn(sentence, pinnedReading);
            if (cited.isEmpty()) {
                continue;
            }
            String sentenceText = CITATION_MARKER.matcher(sentence).replaceAll(" ");
            boolean rose = false;
            boolean fell = false;
            boolean steady = false;
            boolean quicker = false;
            boolean slower = false;
            for (TurnContext.PinnedFact fact : cited) {
                String note = fact.note() == null ? "" : fact.note();
                String label = fact.label() == null ? "" : fact.label();
                rose = rose || note.startsWith("Up") || label.endsWith(" record");
                fell = fell || note.startsWith("Down");
                steady = steady || "Unchanged".equals(note);
                quicker = quicker || note.endsWith(" faster");
                slower = slower || note.endsWith(" slower");
            }
            int sentenceNumber = i + 1;
            if (!rose && CoachVoice.RISING.matcher(sentenceText).find()) {
                violations.add("DIRECTION_NOT_IN_FACTS:rising in sentence " + sentenceNumber);
            }
            if (!fell && CoachVoice.FALLING.matcher(sentenceText).find()) {
                violations.add("DIRECTION_NOT_IN_FACTS:falling in sentence " + sentenceNumber);
            }
            if (!steady && CoachVoice.STEADY.matcher(sentenceText).find()) {
                violations.add("DIRECTION_NOT_IN_FACTS:unchanged in sentence " + sentenceNumber);
            }
            if (!quicker && CoachVoice.FASTER.matcher(sentenceText).find()) {
                violations.add("DIRECTION_NOT_IN_FACTS:faster in sentence " + sentenceNumber);
            }
            if (!slower && CoachVoice.SLOWER.matcher(sentenceText).find()) {
                violations.add("DIRECTION_NOT_IN_FACTS:slower in sentence " + sentenceNumber);
            }
        }
        return violations;
    }

    /**
     * The mistake seen in testing: a per-session fact's value, restated as though it covered a
     * week. Deliberately narrow, exactly as specified — a sentence trips this only when it names the
     * fact by citation, reads as weekly, and never uses the word "session" itself.
     */
    private static List<String> measureMismatchViolations(String answer, TurnContext.PinnedReading pinnedReading) {
        List<String> violations = new ArrayList<>();
        String[] sentences = SENTENCE_END.split(answer.strip());
        for (String sentence : sentences) {
            String lowerSentence = sentence.toLowerCase(Locale.ROOT);
            if (lowerSentence.contains("session") || !WEEKLY_MEASURE.matcher(lowerSentence).find()) {
                continue;
            }
            for (TurnContext.PinnedFact fact : factsCitedIn(sentence, pinnedReading)) {
                String label = fact.label() == null ? "" : fact.label().toLowerCase(Locale.ROOT);
                if (label.contains("per session")) {
                    violations.add("MEASURE_MISMATCH:" + fact.number());
                }
            }
        }
        return violations;
    }

    /**
     * The computed facts count the last full week, and a question about "my week" is about the
     * current one. A sentence that states last week's count of sessions or minutes has to say that
     * it is last week's. The rule runs only when the two weeks are both known and differ, and it
     * leaves a count alone when the current week has the same one.
     */
    private static List<String> weekNotNamedViolations(String answer, WeekFigures weeks) {
        List<String> violations = new ArrayList<>();
        if (!weeks.comparable()) {
            return violations;
        }
        for (String sentence : SENTENCE_END.split(answer.strip())) {
            if (EARLIER_PERIOD.matcher(sentence).find()) {
                continue;
            }
            Matcher matcher = COUNTED.matcher(sentence);
            while (matcher.find()) {
                int stated = parseNumber(matcher.group(1)).intValue();
                boolean minutes = matcher.group(2).toLowerCase(Locale.ROOT).startsWith("min");
                Integer lastWeek = minutes ? weeks.lastWeekMinutes() : weeks.lastWeekSessions();
                Integer currentWeek = minutes ? weeks.currentWeekMinutes() : weeks.currentWeekSessions();
                if (lastWeek != null && stated == lastWeek && stated != currentWeek) {
                    violations.add("WEEK_NOT_NAMED:" + matcher.group(1) + " " + matcher.group(2));
                }
            }
        }
        return violations;
    }

    /**
     * A reading describes the last full week until the current one holds enough sessions. A
     * sentence that cites one of its facts and places it in "this week" is then false, however
     * right the number is. Only that direction is checked: when the reading describes the current
     * week, its facts compare against the week before, so "last week" beside a citation is true.
     */
    private static List<String> periodMismatchViolations(String answer, TurnContext.PinnedReading pinnedReading) {
        List<String> violations = new ArrayList<>();
        TurnContext.PinnedPeriod period = pinnedReading.period();
        if (period == null || period.currentWeek()) {
            return violations;
        }
        for (String sentence : SENTENCE_END.split(answer.strip())) {
            if (!CURRENT_WEEK.matcher(sentence).find()) {
                continue;
            }
            for (TurnContext.PinnedFact fact : factsCitedIn(sentence, pinnedReading)) {
                violations.add("PERIOD_MISMATCH:" + fact.number());
            }
        }
        return violations;
    }

    private static List<TurnContext.PinnedFact> factsCitedIn(String sentence, TurnContext.PinnedReading pinnedReading) {
        List<TurnContext.PinnedFact> cited = new ArrayList<>();
        for (Integer number : citationsIn(sentence)) {
            for (TurnContext.PinnedFact fact : pinnedReading.facts()) {
                if (fact.number() == number) {
                    cited.add(fact);
                }
            }
        }
        return cited;
    }

    private static List<Integer> citationsIn(String text) {
        List<Integer> citations = new ArrayList<>();
        Matcher matcher = CITATION_MARKER.matcher(text);
        while (matcher.find()) {
            citations.add(Integer.valueOf(matcher.group(1)));
        }
        return citations;
    }

    /**
     * @param valid      whether the answer may be returned to the user as it is
     * @param violations empty when valid; otherwise every rule the answer broke, for the correction
     *                   prompt and for tests to assert on
     */
    public record Result(boolean valid, List<String> violations) {

        public Result {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        @Override
        public List<String> violations() {
            return List.copyOf(violations);
        }
    }
}
