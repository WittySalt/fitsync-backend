package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
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

    /** A hyphen with a space on each side, or two in a row, stands in for a dash. */
    private static final Pattern DASH_AS_PUNCTUATION = Pattern.compile("\\s-\\s|--");

    /**
     * A number token: either comma-grouped ({@code 1,923}) or a plain digit run ({@code 1923}),
     * with an optional decimal tail. A trailing {@code %} is not part of the match, so {@code 12%}
     * reads as the number 12 with the sign left as surrounding text.
     */
    private static final Pattern NUMBER_TOKEN =
            Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?");

    private static final Pattern TRY_AT_SENTENCE_START = Pattern.compile("(?i)(?:^|[.]\\s+)try\\s");

    private static final List<String> BANNED_PHRASES = List.of(
            "you should", "you must", "you need to", "you ought", "make sure", "be sure to",
            "try to", "consider ", "aim to", "aim for", "remember to", "don't ", "do not ",
            "let's", "it's time to", "time to ");

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
        for (String violation : toneViolations(combined)) {
            violations.add(violation);
        }

        if (!isPrintableAsciiOrApostrophe(combined)) {
            violations.add("NON_ASCII_CHARACTER");
        }

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

    private static List<String> toneViolations(String text) {
        List<String> violations = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : BANNED_PHRASES) {
            if (lower.contains(phrase)) {
                violations.add("BANNED_PHRASE:" + phrase.strip());
            }
        }
        if (TRY_AT_SENTENCE_START.matcher(text).find()) {
            violations.add("BANNED_PHRASE:try-at-sentence-start");
        }
        if (text.contains("?")) {
            violations.add("QUESTION_MARK");
        }
        if (text.contains("!")) {
            violations.add("EXCLAMATION_MARK");
        }
        if (DASH_AS_PUNCTUATION.matcher(text).find()) {
            violations.add("DASH_AS_PUNCTUATION");
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
