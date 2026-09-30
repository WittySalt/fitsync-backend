package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import com.fitsync.recommendation.coach.guardrail.CoachVoice;
import org.springframework.stereotype.Component;

/**
 * The two rules that matter more than anything the model was told: a reading never tells the user
 * what to do, and every number in it is one of the measured facts. A well-behaved model mostly
 * follows its system prompt; this is the check that runs regardless of how well it followed it.
 *
 * <p>Pure and deterministic — no I/O, no model call — so it can be exercised directly against the
 * worked example and against adversarial text without a running service. {@link CoachGuardrail} is
 * threaded through as well, on the same reasoning the model-answer number check rests on: the system
 * prompt tells the model never to suggest an unsafe calorie floor, and this is the rule that runs
 * whether or not it listened.
 */
@Component
public class ReadingValidator {

    private static final int MAX_HEADLINE_LENGTH = 80;
    private static final int MAX_BODY_LENGTH = 320;

    /**
     * One or two digits. A longer run of digits in brackets is not a citation, so it is left in the
     * text and fails the number check, and it can never overflow when parsed.
     */
    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    /**
     * A number token: either comma-grouped ({@code 1,923}) or a plain digit run ({@code 1923}),
     * with an optional decimal tail. A trailing {@code %} is not part of the match, so {@code 12%}
     * reads as the number 12 with the sign left as surrounding text.
     */
    private static final Pattern NUMBER_TOKEN =
            Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?");

    /**
     * A claim that something did not happen. No fact can confirm it, because the facts only record
     * what was measured, so the number check passes it however wrong it is. A small model wrote
     * "no record was set" beside a fact that was a record, and that answer contained no number at
     * all that could have failed.
     */
    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(no|not|never|none|nothing|nobody|without|neither|nor|cannot)\\b|n['\u2019]t\\b");

    /** A sentence ends at a full stop that is followed by a space, so 22.5 stays whole. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.])\\s+");

    private final CoachGuardrail guardrail;

    public ReadingValidator(CoachGuardrail guardrail) {
        this.guardrail = guardrail;
    }

    public Result validate(String headline, String body, List<ReadingFact> facts) {
        List<String> violations = new ArrayList<>();

        if (headline == null || headline.isBlank()) {
            violations.add("HEADLINE_BLANK");
        } else {
            if (headline.length() > MAX_HEADLINE_LENGTH) {
                violations.add("HEADLINE_TOO_LONG");
            }
            if (headline.contains("\n")) {
                violations.add("HEADLINE_MULTI_LINE");
            }
        }

        if (body == null || body.isBlank()) {
            violations.add("BODY_BLANK");
        } else {
            if (body.length() > MAX_BODY_LENGTH) {
                violations.add("BODY_TOO_LONG");
            }
            if (body.contains("\n")) {
                violations.add("BODY_MULTI_LINE");
            }
        }

        // Nothing further can be checked meaningfully against missing text.
        if (headline == null || headline.isBlank() || body == null || body.isBlank()) {
            return new Result(false, List.copyOf(violations), List.of());
        }

        List<Integer> citations = citationsIn(body);
        if (citations.isEmpty()) {
            violations.add("NO_CITATION");
        }
        Set<Integer> factNumbers = new LinkedHashSet<>();
        for (ReadingFact fact : facts) {
            factNumbers.add(fact.number());
        }
        for (Integer citation : citations) {
            if (!factNumbers.contains(citation)) {
                violations.add("INVALID_CITATION:" + citation);
            }
        }

        Set<BigDecimal> allowedNumbers = allowedNumbers(facts);
        String cleanedHeadline = stripCitations(headline);
        String cleanedBody = stripCitations(body);
        for (String badNumber : numbersNotIn(cleanedHeadline, allowedNumbers)) {
            violations.add("NUMBER_NOT_IN_FACTS:" + badNumber);
        }
        for (String badNumber : numbersNotIn(cleanedBody, allowedNumbers)) {
            violations.add("NUMBER_NOT_IN_FACTS:" + badNumber);
        }

        String combined = headline + " " + body;
        for (String violation : CoachVoice.toneViolations(combined)) {
            violations.add(violation);
        }

        if (!isPrintableAsciiOrApostrophe(combined)) {
            violations.add("NON_ASCII_CHARACTER");
        }

        if (NEGATION.matcher(combined).find()) {
            violations.add("NEGATION");
        }
        violations.addAll(directionViolations(headline, facts, "headline"));
        violations.addAll(sentenceViolations(body, facts));

        guardrail.check(combined).ifPresent(refusal -> violations.add("GUARDRAIL:" + refusal.rule()));

        boolean valid = violations.isEmpty();
        return new Result(valid, List.copyOf(violations), valid ? citations : List.of());
    }

    private static List<Integer> citationsIn(String body) {
        Set<Integer> citations = new TreeSet<>();
        Matcher matcher = CITATION.matcher(body);
        while (matcher.find()) {
            citations.add(Integer.valueOf(matcher.group(1)));
        }
        return List.copyOf(citations);
    }

    private static String stripCitations(String text) {
        return CITATION.matcher(text).replaceAll(" ");
    }

    private static Set<BigDecimal> allowedNumbers(List<ReadingFact> facts) {
        Set<BigDecimal> allowed = new LinkedHashSet<>();
        for (ReadingFact fact : facts) {
            if (fact.numericValue() != null) {
                allowed.add(fact.numericValue().stripTrailingZeros());
            }
            addNumbersFrom(allowed, fact.value());
            addNumbersFrom(allowed, fact.unit());
            addNumbersFrom(allowed, fact.note());
            addNumbersFrom(allowed, fact.detail());
        }
        return allowed;
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

    private static List<String> numbersNotIn(String text, Set<BigDecimal> allowedNumbers) {
        List<String> offending = new ArrayList<>();
        Matcher matcher = NUMBER_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            BigDecimal parsed = parseNumber(token).stripTrailingZeros();
            boolean allowed = allowedNumbers.stream().anyMatch(candidate -> candidate.compareTo(parsed) == 0);
            if (!allowed) {
                offending.add(token);
            }
        }
        return offending;
    }

    private static BigDecimal parseNumber(String token) {
        return new BigDecimal(token.replace(",", ""));
    }

    /**
     * Checks the body one sentence at a time against the facts that sentence cites.
     *
     * <p>Every sentence must cite a fact, so that every claim can be traced. A sentence may say
     * that something went up, went down or stayed the same only when one of the facts it cites did
     * so. Checking against all the facts at once was not enough: a small model wrote that a lift
     * "remained unchanged" and cited the record for that lift, while a different fact happened to
     * be unchanged.
     */
    private static List<String> sentenceViolations(String body, List<ReadingFact> facts) {
        List<String> violations = new ArrayList<>();
        String[] sentences = SENTENCE_END.split(body.strip());
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            List<ReadingFact> cited = new ArrayList<>();
            for (Integer number : citationsIn(sentence)) {
                for (ReadingFact fact : facts) {
                    if (fact.number() == number) {
                        cited.add(fact);
                    }
                }
            }
            if (cited.isEmpty()) {
                violations.add("SENTENCE_WITHOUT_CITATION:" + (i + 1));
                continue;
            }
            violations.addAll(directionViolations(stripCitations(sentence), cited, "sentence " + (i + 1)));
        }
        return violations;
    }

    /**
     * A fact went up when its note begins with "Up" or when it is a record. It went down when its
     * note begins with "Down", and it stayed the same when its note is "Unchanged". A text may use
     * a word for one of these only when one of the given facts did so. A pace fact says faster or
     * slower in its note, and "faster" and "slower" are held to it the same way.
     */
    private static List<String> directionViolations(String text, List<ReadingFact> facts, String where) {
        boolean rose = false;
        boolean fell = false;
        boolean steady = false;
        boolean quicker = false;
        boolean slower = false;
        boolean paceSteady = false;
        for (ReadingFact fact : facts) {
            String note = fact.note() == null ? "" : fact.note();
            rose = rose || note.startsWith("Up") || "RECORD".equals(fact.key());
            fell = fell || note.startsWith("Down");
            steady = steady || note.startsWith("Unchanged");
            quicker = quicker || note.endsWith(" faster");
            slower = slower || note.endsWith(" slower");
            paceSteady = paceSteady || ("CARDIO_PACE".equals(fact.key()) && note.startsWith("Unchanged"));
        }
        List<String> violations = new ArrayList<>();
        if (!rose && CoachVoice.RISING.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:rising in the " + where);
        }
        if (!fell && CoachVoice.FALLING.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:falling in the " + where);
        }
        if (!steady && CoachVoice.STEADY.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:unchanged in the " + where);
        }
        if (!quicker && CoachVoice.FASTER.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:faster in the " + where);
        }
        if (!slower && CoachVoice.SLOWER.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:slower in the " + where);
        }
        // "The same pace" borrows its word from another fact that stayed the same, a session length
        // for example. A text that names the pace may only call it steady when the pace itself was.
        if (!paceSteady && CoachVoice.PACE.matcher(text).find() && CoachVoice.STEADY.matcher(text).find()) {
            violations.add("DIRECTION_NOT_IN_FACTS:pace unchanged in the " + where);
        }
        return violations;
    }

    private static boolean isPrintableAsciiOrApostrophe(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n' || ch == '\r') {
                continue; // caught separately as a single-line violation
            }
            boolean printableAscii = ch >= 0x20 && ch <= 0x7E;
            boolean rightSingleQuote = ch == '’';
            if (!printableAscii && !rightSingleQuote) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param valid      whether the answer may be stored as the reading
     * @param violations empty when valid; otherwise every rule the answer broke, for the repair
     *                   prompt and for tests to assert on
     * @param citations  the sorted, distinct fact numbers the body cited; only populated when valid
     */
    public record Result(boolean valid, List<String> violations, List<Integer> citations) {

        public Result {
            violations = violations == null ? List.of() : List.copyOf(violations);
            citations = citations == null ? List.of() : List.copyOf(citations);
        }

        public Optional<String> firstViolation() {
            return violations.isEmpty() ? Optional.empty() : Optional.of(violations.get(0));
        }

        // Explicit overrides, re-wrapping in an immutable copy on every call: List.copyOf in the
        // compact constructor above already makes the stored reference unmodifiable, but this is
        // the same belt-and-braces pattern CoachMessageResponse.toolsUsed() uses elsewhere in this
        // module, and it is what actually satisfies findsecbugs' EI_EXPOSE_REP here.
        @Override
        public List<String> violations() {
            return List.copyOf(violations);
        }

        @Override
        public List<Integer> citations() {
            return List.copyOf(citations);
        }
    }
}
