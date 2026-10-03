package com.fitsync.recommendation.coach.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The tone rules the coach's own writing must follow, wherever that writing is checked.
 *
 * <p>Two checkers share this: {@link com.fitsync.recommendation.coach.reading.ReadingValidator}
 * for the page's pinned reading, and {@code AnswerValidator} in {@code coach.orchestrator} for a
 * chat answer. Both packages already import this one — {@code reading} through
 * {@link CoachGuardrail} and {@code orchestrator} the same way — so the phrase list, the direction
 * word lists and the punctuation rules live here once instead of drifting apart the next time either
 * checker is touched. The violation codes are the ones {@code ReadingValidator} has always used.
 */
public final class CoachVoice {

    /** A hyphen with a space on each side, or two in a row, stands in for a dash. */
    public static final Pattern DASH_AS_PUNCTUATION = Pattern.compile("\\s-\\s|--");

    /** A status code or field name copied from a tool result, such as AHEAD or ON_TRACK, instead of plain words. */
    public static final Pattern RAW_STATUS_WORD = Pattern.compile(
            "\\b(?:AHEAD|BEHIND|ON_TRACK|OFF_TRACK|MISSED|NOT_LOGGED|[A-Z]{2,}_[A-Z_]{2,})\\b");

    private static final Pattern TRY_AT_SENTENCE_START = Pattern.compile("(?i)(?:^|[.]\\s+)try\\s");

    /** A word that says a measured thing rose. */
    public static final Pattern RISING = Pattern.compile(
            "(?i)\\b(up|rose|risen|rising|grew|grown|growing|increas(?:e|es|ed|ing)|more|higher|heavier|"
                    + "longer|gain(?:s|ed|ing)?|climb(?:s|ed|ing)?|improv(?:e|es|ed|ing))\\b");

    /** A word that says a measured thing fell. */
    public static final Pattern FALLING = Pattern.compile(
            "(?i)\\b(down|fell|fallen|falling|drop(?:s|ped|ping)?|decreas(?:e|es|ed|ing)|less|lower|"
                    + "lighter|shorter|fewer|declin(?:e|es|ed|ing)|slipp(?:ed|ing))\\b");

    /** A word that says a measured thing stayed the same. */
    public static final Pattern STEADY = Pattern.compile(
            "(?i)\\b(unchanged|same|remain(?:s|ed|ing)?|stay(?:s|ed|ing)?|steady|held|flat|constant)\\b");

    /** A word that says a pace got quicker. A lower number of seconds a kilometre is a faster pace. */
    public static final Pattern FASTER = Pattern.compile("(?i)\\b(faster|quicker)\\b");

    /** The word for how fast a kilometre was covered. */
    public static final Pattern PACE = Pattern.compile("(?i)\\bpace\\b");

    /** A word that says a pace got slower. */
    public static final Pattern SLOWER = Pattern.compile("(?i)\\b(slower)\\b");

    private static final List<String> BANNED_PHRASES = List.of(
            "you should", "you must", "you need to", "you ought", "make sure", "be sure to",
            "try to", "consider ", "aim to", "aim for", "remember to",
            "let's", "it's time to", "time to ");

    /**
     * "Do not" as an instruction: at the start of a sentence or a clause, or after "please". "I do
     * not have your meals from yesterday" and "your records do not show it" are the coach saying
     * what it cannot see, which is the answer it is meant to give, and they pass.
     */
    private static final Pattern DO_NOT_AS_INSTRUCTION = Pattern.compile(
            "(?i)(?:^|[.;:,]\\s+|\\b(?:please|and|but|so|then)\\s+)(?:do not|don't)\\s");

    private CoachVoice() {
    }

    /**
     * The banned-phrase, question-mark, exclamation-mark and dash checks, run over one piece of
     * text. Negation is deliberately not part of this: it belongs to the reading check alone,
     * because a chat answer is allowed to say "that is not in your records".
     *
     * @return every tone violation the text broke, in the same codes {@code ReadingValidator} has
     *         always used: {@code BANNED_PHRASE:<phrase>}, {@code QUESTION_MARK},
     *         {@code EXCLAMATION_MARK}, {@code DASH_AS_PUNCTUATION}
     */
    public static List<String> toneViolations(String text) {
        List<String> violations = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String phrase : BANNED_PHRASES) {
            if (lower.contains(phrase)) {
                violations.add("BANNED_PHRASE:" + phrase.strip());
            }
        }
        if (DO_NOT_AS_INSTRUCTION.matcher(text).find()) {
            violations.add("BANNED_PHRASE:do not");
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
        if (RAW_STATUS_WORD.matcher(text).find()) {
            violations.add("RAW_STATUS_WORD");
        }
        return violations;
    }
}
