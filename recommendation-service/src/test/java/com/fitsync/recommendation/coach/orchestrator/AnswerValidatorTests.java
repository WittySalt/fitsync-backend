package com.fitsync.recommendation.coach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fitsync.recommendation.coach.dto.CoachSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The check the product owner asked for once a chat answer had already attached "up 12%" to the
 * wrong fact and reported a per-session figure as a weekly one. Deterministic, no model involved,
 * the same way {@code ReadingValidatorTests} exercises {@code ReadingValidator}.
 */
class AnswerValidatorTests {

    private final AnswerValidator validator = new AnswerValidator();

    // --- numbers -------------------------------------------------------------------------------

    @Test
    @DisplayName("an answer using only numbers from the transcript passes")
    void numbersFromTheTranscriptPass() {
        List<String> transcript = List.of("Result of readFacts({}):\nYou trained 4 of 5 planned days.");

        AnswerValidator.Result result = validator.validate(
                "You trained 4 of 5 planned days.", "How did my week go?", transcript, null);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("1923, 1,923 and 1,923.0 are numerically the same and all pass")
    void groupingAndTrailingZerosDoNotMatter() {
        List<String> transcript = List.of("Weight moved per session: 1,923 kg.");

        assertThat(validator.validate("You moved 1923 kg.", "q", transcript, null).valid()).isTrue();
        assertThat(validator.validate("You moved 1,923.0 kg.", "q", transcript, null).valid()).isTrue();
    }

    @Test
    @DisplayName("an ordinal such as 25th passes when the transcript holds a matching date")
    void ordinalAgainstADatePasses() {
        List<String> transcript = List.of("Your last session was on 2026-09-25.");

        AnswerValidator.Result result = validator.validate(
                "Your last session was the 25th.", "when was it?", transcript, null);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("a number that only appears in the user's question still passes")
    void numberFromTheQuestionPasses() {
        AnswerValidator.Result result = validator.validate(
                "42 is not a figure any of your records show.", "Is 42 my current streak?", List.of(), null);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("a figure the model worked out for itself, such as an average, fails")
    void aComputedAverageFails() {
        List<String> transcript = List.of("Session A moved 100 kg. Session B moved 200 kg.");

        AnswerValidator.Result result = validator.validate(
                "On average you moved 150 kg a session.", "how am I doing?", transcript, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.startsWith("NUMBER_NOT_IN_RECORDS:150"));
    }

    @Test
    @DisplayName("12% passes when a cited fact's note is Up 12%")
    void percentAgainstAFactNotePasses() {
        TurnContext turnContext = new TurnContext(CoachSurface.WORKOUT, new TurnContext.PinnedReading(
                "h", "b", List.of(new TurnContext.PinnedFact(
                        1, "Weight moved per session", "1,923", "kg", "Up 12%", "1,716 kg the week before"))));

        AnswerValidator.Result result = validator.validate(
                "You moved 1,923 kg a session [1], up 12% from before [1].", "how is my training?",
                List.of(), turnContext);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("citation markers are stripped before the number check, so a citation number is not itself checked")
    void citationMarkersAreNotThemselvesCheckedAsNumbers() {
        TurnContext turnContext = new TurnContext(CoachSurface.WORKOUT, new TurnContext.PinnedReading(
                "h", "b", List.of(
                        new TurnContext.PinnedFact(1, "Weight moved per session", "1,923", "kg", "Up 12%", ""),
                        new TurnContext.PinnedFact(2, "Session length", "50", "min", "Unchanged", ""),
                        new TurnContext.PinnedFact(9, "Sessions with core work", "4", "of 4", "Every one", ""))));

        AnswerValidator.Result result = validator.validate(
                "You moved 1,923 kg a session [1] in 50 min [2].", "q", List.of(), turnContext);

        assertThat(result.valid()).isTrue();
    }

    // --- form ------------------------------------------------------------------------------------

    @Test
    @DisplayName("a blank answer fails with ANSWER_BLANK")
    void blankAnswerFails() {
        assertThat(validator.validate("", "q", List.of(), null).violations()).contains("ANSWER_BLANK");
        assertThat(validator.validate("   ", "q", List.of(), null).violations()).contains("ANSWER_BLANK");
        assertThat(validator.validate(null, "q", List.of(), null).valid()).isFalse();
    }

    @Test
    @DisplayName("an answer over seven hundred characters fails with ANSWER_TOO_LONG")
    void tooLongAnswerFails() {
        String longAnswer = "You trained today. ".repeat(40);

        assertThat(validator.validate(longAnswer, "q", List.of(), null).violations()).contains("ANSWER_TOO_LONG");
    }

    // --- voice -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a banned phrase fails, however it is phrased")
    void bannedPhraseFails() {
        assertThat(validator.validate("You should train more.", "q", List.of(), null).violations())
                .anyMatch(v -> v.startsWith("BANNED_PHRASE:"));
    }

    @Test
    @DisplayName("try at the start of a sentence fails")
    void tryAtSentenceStartFails() {
        assertThat(validator.validate("Try lifting more next time.", "q", List.of(), null).violations())
                .contains("BANNED_PHRASE:try-at-sentence-start");
    }

    @Test
    @DisplayName("a question mark fails, even one that only echoes the user's own question")
    void questionMarkFails() {
        assertThat(validator.validate("Did you mean your last session?", "q", List.of(), null).violations())
                .contains("QUESTION_MARK");
    }

    @Test
    @DisplayName("an exclamation mark fails")
    void exclamationMarkFails() {
        assertThat(validator.validate("Great session!", "q", List.of(), null).violations())
                .contains("EXCLAMATION_MARK");
    }

    @Test
    @DisplayName("a dash used as punctuation fails, and a hyphenated word does not")
    void dashAsPunctuationFails() {
        assertThat(validator.validate("You trained hard - your numbers show it.", "q", List.of(), null).violations())
                .contains("DASH_AS_PUNCTUATION");
        assertThat(validator.validate("Your bent-over row felt strong.", "q", List.of(), null).violations())
                .doesNotContain("DASH_AS_PUNCTUATION");
    }

    @Test
    @DisplayName("negation is allowed in chat, unlike in the pinned reading")
    void negationPasses() {
        AnswerValidator.Result result = validator.validate(
                "That is not something your records show.", "did I set a record?", List.of(), null);

        assertThat(result.valid()).isTrue();
    }

    // --- citations and direction, only checked when a reading is pinned ------------------------

    @Test
    @DisplayName("with no pinned reading, a citation is not checked at all")
    void noCitationCheckWithoutAPinnedReading() {
        AnswerValidator.Result result = validator.validate(
                "You trained 4 days [1].", "q", List.of("You trained 4 days."), null);

        assertThat(result.violations()).noneMatch(v -> v.startsWith("INVALID_CITATION"));
    }

    @Test
    @DisplayName("a citation of a fact number that does not exist fails")
    void invalidCitationFails() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "You moved 1,923 kg a session [7].", "q", List.of(), turnContext);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("INVALID_CITATION:7");
    }

    @Test
    @DisplayName("the worked example passes: both cited facts rose, so up is allowed")
    void theWorkedDirectionExamplePasses() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "Your Dumbbell Bench Press reached 22.5 kg [3], up 12% from the prior week [1].",
                "how is my bench press?", List.of(), turnContext);

        assertThat(result.valid())
                .withFailMessage("both fact 1 (Up 12 percent) and fact 3 (a record) went up, so up is allowed "
                                + "here: %s", result.violations())
                .isTrue();
    }

    @Test
    @DisplayName("a sentence calling a record unchanged fails")
    void callingARecordUnchangedFails() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "Your Dumbbell Bench Press stayed unchanged at 22.5 kg [3].", "how is my bench press?",
                List.of(), turnContext);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.startsWith("DIRECTION_NOT_IN_FACTS:unchanged"));
    }

    @Test
    @DisplayName("a fall is rejected when the cited fact in that sentence rose")
    void aFallIsRejectedWhenTheCitedFactRose() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "Your weight moved per session dropped to 1,923 kg [1].", "how is my training?",
                List.of(), turnContext);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.startsWith("DIRECTION_NOT_IN_FACTS:falling"));
    }

    @Test
    @DisplayName("a sentence with no citation is not checked for direction at all")
    void sentenceWithoutCitationSkipsDirectionCheck() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "The workout catalogue lists a lower rep range for this exercise.", "what about form?",
                List.of("The workout catalogue lists a lower rep range for this exercise."), turnContext);

        assertThat(result.violations()).noneMatch(v -> v.startsWith("DIRECTION_NOT_IN_FACTS"));
    }

    // --- measure mismatch: the other mistake seen in testing -------------------------------------

    @Test
    @DisplayName("a per-session fact restated as a weekly figure fails with MEASURE_MISMATCH")
    void perSessionStatedAsWeeklyFails() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "Overall, you've moved 1,923 kg this week [1].", "how is my week going?", List.of(), turnContext);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("MEASURE_MISMATCH:1");
    }

    @Test
    @DisplayName("the same figure named as a per-session figure passes")
    void perSessionStatedAsPerSessionPasses() {
        TurnContext turnContext = pinnedReadingWithTheWorkedExampleFacts();

        AnswerValidator.Result result = validator.validate(
                "Each session moved 1,923 kg [1] this week.", "how is my week going?", List.of(), turnContext);

        assertThat(result.valid()).isTrue();
    }

    /** The reading from the worked example {@code ReadingValidatorTests} also uses. */
    private static TurnContext pinnedReadingWithTheWorkedExampleFacts() {
        TurnContext.PinnedReading pinnedReading = new TurnContext.PinnedReading(
                "Your lifts grew while your sessions stayed the same length.",
                "You moved 1,923 kg a session [1], up from 1,716 kg, and each one still took 50 min [2].",
                List.of(
                        new TurnContext.PinnedFact(1, "Weight moved per session", "1,923", "kg",
                                "Up 12%", "1,716 kg the week before"),
                        new TurnContext.PinnedFact(2, "Session length", "50", "min",
                                "Unchanged", "50 min the week before"),
                        new TurnContext.PinnedFact(3, "Dumbbell Bench Press record", "22.5", "kg",
                                "Monday", "20 kg before"),
                        new TurnContext.PinnedFact(4, "Sessions with core work", "4", "of 4",
                                "Every one", "Plank Hold")));
        return new TurnContext(CoachSurface.WORKOUT, pinnedReading);
    }
}
