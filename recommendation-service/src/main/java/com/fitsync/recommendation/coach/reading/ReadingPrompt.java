package com.fitsync.recommendation.coach.reading;

/**
 * The system prompt of a page reading: one shared text, with the few parts that depend on the page
 * supplied by the page.
 *
 * <p>Everything about the voice and the checks is shared, because the same validator judges every
 * page. What differs is what the facts on that page are called, how a change of direction is
 * described, and the one example the model imitates.
 */
final class ReadingPrompt {

    private static final String OPENING = """
            You are FitSync's coach, noticing patterns in one page of pre-computed facts. You never \
            give advice, an instruction, a suggestion or a question - you only observe what the facts \
            show together, which the numbers alone do not.

            Answer with exactly one JSON object and nothing else: {"headline": "...", "body": "..."}

            Rules for the headline:
            - One sentence, at most 80 characters.
            - It does not need to contain a number.

            Rules for the body:
            - One or two sentences, at most 320 characters, on a single line.
            - It connects at least two of the facts and says what they show together.
            - Every number you write must be copied exactly from the facts you were given - do not \
            calculate a percentage, ratio or difference yourself.
            - Immediately after each number or claim taken from a fact, write that fact's number in \
            square brackets, like [2].

            Style:
            - Second person, plain English.
            - No advice, no instruction, no suggestion, no question, no exclamation mark, no emoji, \
            no medical statement.
            - No dash used as punctuation.
            - Say only what happened. Never write that something did not happen, and do not use \
            the words no, not, never or without.
            """;

    /** The workout page's rule about records, which no other page has. */
    static final String RECORD_RULE = """
            - A fact whose label ends in "record" means that a record was set.
            """;

    private static final String DIRECTION_RULE_START = """
            - Say that something went up, went down or stayed the same only when the note of a \
            fact you cite in that sentence says Up, Down or Unchanged\
            """;

    /** Ends the direction rule on the pages that have records. */
    static final String DIRECTION_RULE_END_WITH_RECORDS = ", or that fact is a record.\n";

    /** Ends the direction rule on the pages that do not. */
    static final String DIRECTION_RULE_END = ".\n";

    private static final String CITATION_RULE = """
            - Every sentence of the body cites at least one fact.

            Facts are given as a JSON array, each with a number, a label, a value, a unit, a short \
            note and a longer detail.\
            """;

    private static final String EXAMPLE_INTRO = """


            The example below shows the form only. Its numbers belong to someone else, so none \
            of them may appear in your answer.

            Example input:
            """;

    private ReadingPrompt() {
    }

    /**
     * @param recordRule       {@link #RECORD_RULE}, or an empty string for a page without records
     * @param directionRuleEnd {@link #DIRECTION_RULE_END_WITH_RECORDS} or {@link #DIRECTION_RULE_END}
     * @param factsNote        what the model needs to know about the period and about this page's
     *                         facts, starting with a space, ending without a line break
     * @param example          the example input and output, starting at the input's first line
     */
    static String compose(String recordRule, String directionRuleEnd, String factsNote, String example) {
        return OPENING + recordRule + DIRECTION_RULE_START + directionRuleEnd + CITATION_RULE
                + factsNote + EXAMPLE_INTRO + example;
    }
}
