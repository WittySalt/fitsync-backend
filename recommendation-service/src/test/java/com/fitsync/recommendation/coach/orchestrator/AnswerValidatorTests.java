package com.fitsync.recommendation.coach.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

    @Test
    @DisplayName("a fact from the last full week cannot be placed in this week")
    void rejectsAFactFromTheLastFullWeekPlacedInThisWeek() {
        TurnContext turnContext = describing(new TurnContext.PinnedPeriod(
                false, LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27")));

        AnswerValidator.Result result = validator.validate(
                "Core work was part of all 4 of your sessions this week [4].",
                "Should my core get a day off", List.of(), turnContext);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).containsExactly("PERIOD_MISMATCH:4");
    }

    @Test
    @DisplayName("the same sentence passes when the reading describes the current week")
    void acceptsThisWeekWhenTheReadingDescribesTheCurrentWeek() {
        TurnContext turnContext = describing(new TurnContext.PinnedPeriod(
                true, LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-25")));

        AnswerValidator.Result result = validator.validate(
                "Core work was part of all 4 of your sessions this week [4].",
                "Should my core get a day off", List.of(), turnContext);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("this week without a citation says nothing about the pinned facts")
    void acceptsThisWeekInASentenceThatCitesNothing() {
        TurnContext turnContext = describing(new TurnContext.PinnedPeriod(
                false, LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27")));

        AnswerValidator.Result result = validator.validate(
                "Your records hold 1 session this week. Core work was part of all 4 sessions [4] the week before.",
                "Should my core get a day off", List.of("Result of readFacts({}): 1 session"), turnContext);

        assertThat(result.valid()).isTrue();
    }

    private static final List<String> TWO_WEEKS = List.of("Result of readFacts({}):\n" + """
            {
              "weekStart" : "2026-09-21",
              "training" : {
                "period" : "LAST_FULL_WEEK",
                "targetDaysPerWeek" : 4,
                "activeDays" : 4,
                "status" : "MET",
                "workoutCount" : 4,
                "totalMinutes" : 170
              },
              "trainingThisWeek" : {
                "period" : "CURRENT_WEEK_SO_FAR",
                "weekStart" : "2026-09-28",
                "sessions" : 1,
                "totalMinutes" : 50
              }
            }""");

    @Test
    @DisplayName("last week's count cannot stand for the week without being named")
    void rejectsLastWeeksCountGivenAsTheWeek() {
        AnswerValidator.Result result = validator.validate(
                "Your week is on track. You completed 4 workouts for 170 minutes.",
                "How is my week going", TWO_WEEKS, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).containsExactly("WEEK_NOT_NAMED:4 workouts", "WEEK_NOT_NAMED:170 minutes");
    }

    @Test
    @DisplayName("the same count passes when the sentence says it is last week's")
    void acceptsLastWeeksCountWhenTheSentenceNamesTheWeek() {
        AnswerValidator.Result result = validator.validate(
                "You have 1 session and 50 minutes so far this week. "
                        + "Last week you completed 4 sessions for 170 minutes, against a target of 4.",
                "How is my week going", TWO_WEEKS, null);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("a count the two weeks share is left alone")
    void leavesACountTheTwoWeeksShare() {
        List<String> sameCounts = List.of(TWO_WEEKS.get(0)
                .replace("\"sessions\" : 1", "\"sessions\" : 4")
                .replace("\"totalMinutes\" : 50", "\"totalMinutes\" : 170"));

        AnswerValidator.Result result = validator.validate(
                "You completed 4 workouts for 170 minutes.", "How is my week going", sameCounts, null);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("without the current week there is nothing to compare, and the rule stays out")
    void staysOutWhenTheCurrentWeekIsUnknown() {
        List<String> lastWeekOnly = List.of(TWO_WEEKS.get(0).replace("trainingThisWeek", "somethingElse"));

        AnswerValidator.Result result = validator.validate(
                "You completed 4 workouts for 170 minutes.", "How is my week going", lastWeekOnly, null);

        assertThat(result.valid()).isTrue();
    }

    private static TurnContext describing(TurnContext.PinnedPeriod period) {
        TurnContext.PinnedReading reading = pinnedReadingWithTheWorkedExampleFacts().pinnedReading();
        return new TurnContext(CoachSurface.WORKOUT, new TurnContext.PinnedReading(
                reading.headline(), reading.body(), reading.facts(), period));
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

    // --- numbers written as words --------------------------------------------------------------

    private static final List<String> FOUR_SESSIONS = List.of(
            "Result of readFacts({}):\nYou trained 4 sessions for 170 minutes over 21 days.");

    @Test
    @DisplayName("a count in words that matches the records passes")
    void aCorrectCountInWordsPasses() {
        assertThat(validator.validate("You logged four sessions.", "q", FOUR_SESSIONS, null).valid()).isTrue();
        assertThat(validator.validate("That was twenty-one days.", "q", FOUR_SESSIONS, null).valid()).isTrue();
    }

    @Test
    @DisplayName("a wrong count in words fails with the same code and form as digits")
    void aWrongCountInWordsFails() {
        AnswerValidator.Result result = validator.validate(
                "You logged five sessions.", "q", FOUR_SESSIONS, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).containsExactly("NUMBER_NOT_IN_RECORDS:five");
        assertThat(validator.validate("You logged 5 sessions.", "q", FOUR_SESSIONS, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:5");
    }

    @Test
    @DisplayName("a hyphenated number is read whole, and a wrong one is reported whole")
    void hyphenatedNumbersAreReadWhole() {
        AnswerValidator.Result result = validator.validate(
                "That was twenty-two days.", "q", FOUR_SESSIONS, null);

        assertThat(result.violations()).containsExactly("NUMBER_NOT_IN_RECORDS:twenty-two");
    }

    @Test
    @DisplayName("one as a pronoun, a second, and an idiom are not counts")
    void wordsThatAreNotCountsAreLeftAlone() {
        List<String> records = List.of("Result of readFacts({}):\nYou trained 4 sessions.");

        assertThat(validator.validate("The one you asked about is logged.", "q", records, null).valid()).isTrue();
        assertThat(validator.validate("One of them was a long session.", "q", records, null).valid()).isTrue();
        assertThat(validator.validate("Give me a second to look.", "q", records, null).valid()).isTrue();
        assertThat(validator.validate("Your first session was short, and a day off followed.", "q", records, null)
                .valid()).isTrue();
    }

    @Test
    @DisplayName("a word number opening a sentence is read, whatever its case")
    void aWordNumberAtTheStartOfASentenceIsRead() {
        AnswerValidator.Result result = validator.validate(
                "Six sessions are on record. Four sessions were strength.", "q", FOUR_SESSIONS, null);

        assertThat(result.violations()).containsExactly("NUMBER_NOT_IN_RECORDS:Six");
    }

    @Test
    @DisplayName("digits and words in one answer are each checked")
    void digitsAndWordsAreCheckedTogether() {
        assertThat(validator.validate("Four sessions in 21 days.", "q", FOUR_SESSIONS, null).valid()).isTrue();

        AnswerValidator.Result result = validator.validate(
                "Four sessions in 22 days, and seven workouts.", "q", FOUR_SESSIONS, null);
        assertThat(result.violations()).containsExactly("NUMBER_NOT_IN_RECORDS:22", "NUMBER_NOT_IN_RECORDS:seven");
    }

    @Test
    @DisplayName("grams and kcal rounded to whole numbers and kg to one decimal pass, a made-up figure does not")
    void roundedFiguresPass() {
        List<String> records = List.of("Result of readFacts({}):\n{\"proteinConsumedGrams\": 29.15, "
                + "\"consumedCalories\": 1849.6, \"latestWeightKg\": 71.24}");

        assertThat(validator.validate("You have had 29 g of protein and 1850 kcal, and you weigh 71.2 kg.", "q",
                records, null).valid()).isTrue();
        assertThat(validator.validate("You have had 30 g of protein.", "q", records, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:30");
        assertThat(validator.validate("You weigh 71.24 kg, about 71.25.", "q", records, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:71.25");
    }

    @Test
    @DisplayName("four sessions and three of four planned days are checked like digits")
    void numberWordsOneToTwelveAreChecked() {
        List<String> records = List.of("Result of readFacts({}):\n{\"sessions\": 4, \"plannedDays\": 4, "
                + "\"plannedDaysDone\": 3}");

        assertThat(validator.validate("You did four sessions, three of four planned days.", "q", records, null)
                .valid()).isTrue();
        assertThat(validator.validate("You did five of four planned days.", "q", records, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:five");
        assertThat(validator.validate("You did three of 5 planned days.", "q", records, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:5");
        assertThat(validator.validate("You had eleven grams of fibre.", "q", records, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:eleven");
    }

    @Test
    @DisplayName("a count written in words before a unit is checked too, and a for-a-week count must be named")
    void wordCountsFollowTheWeekRule() {
        AnswerValidator.Result result = validator.validate(
                "Your week is on track. You completed four workouts.", "How is my week going", TWO_WEEKS, null);

        assertThat(result.violations()).containsExactly("WEEK_NOT_NAMED:four workouts");
        assertThat(validator.validate("Last week you completed four workouts.", "q", TWO_WEEKS, null).valid())
                .isTrue();
    }
}
