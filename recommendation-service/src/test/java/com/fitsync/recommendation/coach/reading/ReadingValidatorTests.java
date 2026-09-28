package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The two rules that matter more than the prompt: no advice, and no number that is not one of the
 * facts. Deterministic, no model involved.
 */
class ReadingValidatorTests {

    private static final String HEADLINE = "Your lifts grew while your sessions stayed the same length.";
    private static final String BODY =
            "You moved 1,923 kg a session [1], up from 1,716 kg, and each one still took 50 min [2].";

    private final ReadingValidator validator = new ReadingValidator(new CoachGuardrail());
    private final List<ReadingFact> facts = exampleFacts();

    @Test
    @DisplayName("the worked example passes, with its citations recorded in order")
    void theWorkedExamplePasses() {
        ReadingValidator.Result result = validator.validate(HEADLINE, BODY, facts);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.citations()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("a number the model worked out itself, and that is not one of the facts, fails")
    void aNumberNotInTheFactsFails() {
        String body = "You moved 1,923 kg a session [1], a 99% increase from before [1].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.startsWith("NUMBER_NOT_IN_FACTS:99"));
    }

    @Test
    @DisplayName("1923 and 1,923.0 are numerically the same fact number and both pass")
    void groupingAndTrailingZerosDoNotMatter() {
        String body = "You moved 1923 kg a session [1], which reads the same as 1,923.0 kg [1].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("a number that only appears in a fact's detail still passes")
    void aNumberFromADetailPasses() {
        String body = "Last week you moved 1,716 kg a session [1], and this week you did more [1].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("a citation to a fact number that does not exist fails")
    void aCitationToAMissingFactFails() {
        String body = "You moved 1,923 kg a session [5], up from 1,716 kg, and each one took 50 min [2].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("INVALID_CITATION:5");
    }

    @Test
    @DisplayName("a body with no citation at all fails")
    void noCitationFails() {
        String body = "You moved 1,923 kg a session, up from 1,716 kg, and each one took 50 min.";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("NO_CITATION");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "You should train more [1].", "You must train more [1].", "You need to train more [1].",
            "You ought to train more [1].", "Make sure to rest [1].", "Be sure to rest [1].",
            "Try to rest more [1].", "Consider resting more [1].", "Aim to lift more [1].",
            "Aim for more volume [1].", "Remember to rest [1].", "Don't skip the next session [1].",
            "Do not skip the next session [1].", "Let's train more [1].", "It's time to rest [1].",
            "Time to rest more [1]."
    })
    @DisplayName("each banned phrase is rejected as advice, however it is phrased")
    void bannedPhrasesFail(String body) {
        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid())
                .withFailMessage("expected \"%s\" to be rejected as advice", body)
                .isFalse();
    }

    @Test
    @DisplayName("try at the very start of the body is rejected even without the word to")
    void tryAtStartOfSentenceFails() {
        String body = "Try lifting a bit more [1] next time.";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("a question mark fails")
    void aQuestionMarkFails() {
        String body = "Did you notice your sessions moved 1,923 kg [1]?";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("QUESTION_MARK");
    }

    @Test
    @DisplayName("an exclamation mark fails")
    void anExclamationMarkFails() {
        String body = "You moved 1,923 kg a session [1]!";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("EXCLAMATION_MARK");
    }

    @Test
    @DisplayName("an emoji fails as a non-ASCII character")
    void anEmojiFails() {
        String body = "You moved 1,923 kg a session 💪 [1].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("NON_ASCII_CHARACTER");
    }

    @Test
    @DisplayName("a headline over eighty characters fails")
    void headlineTooLongFails() {
        String longHeadline = "A".repeat(81);

        ReadingValidator.Result result = validator.validate(longHeadline, BODY, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("HEADLINE_TOO_LONG");
    }

    @Test
    @DisplayName("a body over three hundred and twenty characters fails")
    void bodyTooLongFails() {
        String longBody = "You moved a lot of weight [1]. " + "A".repeat(320);

        ReadingValidator.Result result = validator.validate(HEADLINE, longBody, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("BODY_TOO_LONG");
    }

    @Test
    @DisplayName("a two-line body fails")
    void twoLineBodyFails() {
        String body = "You moved 1,923 kg a session [1].\nUp from 1,716 kg [1].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).contains("BODY_MULTI_LINE");
    }

    @Test
    @DisplayName("a blank headline or body fails without throwing")
    void blankTextFailsCleanly() {
        assertThat(validator.validate("", BODY, facts).violations()).contains("HEADLINE_BLANK");
        assertThat(validator.validate(HEADLINE, "  ", facts).violations()).contains("BODY_BLANK");
        assertThat(validator.validate(null, null, facts).valid()).isFalse();
    }

    @Test
    @DisplayName("a hyphen used as a dash fails, and a hyphen inside a word does not")
    void aDashAsPunctuationFails() {
        String dashed = "You moved 1,923 kg a session [1] - each one still took 50 min [2].";
        String doubled = "You moved 1,923 kg a session [1]--each one still took 50 min [2].";
        String hyphenated = "Your bent-over row and your bench press both moved 1,923 kg a session [1].";

        assertThat(validator.validate(HEADLINE, dashed, facts).violations()).contains("DASH_AS_PUNCTUATION");
        assertThat(validator.validate(HEADLINE, doubled, facts).violations()).contains("DASH_AS_PUNCTUATION");
        assertThat(validator.validate(HEADLINE, hyphenated, facts).valid()).isTrue();
    }

    @Test
    @DisplayName("a long run of digits in brackets is treated as a number, and nothing is thrown")
    void anOversizedCitationIsANumberAndNeverThrows() {
        String body = "You moved 1,923 kg a session [1], as seen before [99999999999999999999].";

        ReadingValidator.Result result = validator.validate(HEADLINE, body, facts);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.startsWith("NUMBER_NOT_IN_FACTS:99999999999999999999"));
    }

    private static List<ReadingFact> exampleFacts() {
        return List.of(
                new ReadingFact(1, "VOLUME_PER_SESSION", "Weight moved per session", "1,923", "kg",
                        new BigDecimal("1923.00"), "Up 12%", "1,716 kg the week before"),
                new ReadingFact(2, "SESSION_LENGTH", "Session length", "50", "min",
                        new BigDecimal("50"), "Unchanged", "50 min the week before"),
                new ReadingFact(3, "RECORD", "Dumbbell Bench Press record", "22.5", "kg",
                        new BigDecimal("22.5"), "Monday", "20 kg before"),
                new ReadingFact(4, "MOST_TRAINED", "Sessions with core work", "4", "of 4",
                        new BigDecimal("4"), "Every one", "Plank Hold"));
    }
}
