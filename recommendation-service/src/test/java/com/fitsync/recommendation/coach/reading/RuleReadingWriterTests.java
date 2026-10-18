package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The default, model-free reading: fixed templates chosen from the direction each fact's note
 * carries, and never any text the validator itself would reject.
 */
class RuleReadingWriterTests {

    private final ReadingValidator validator = new ReadingValidator(new CoachGuardrail());
    private final ReadingFactsBuilder factsBuilder = new ReadingFactsBuilder();
    private final RuleReadingWriter writer = new RuleReadingWriter(validator);

    @Test
    @DisplayName("the demo case: best single up, length unchanged, a record and a most-trained group")
    void theDemoCase() {
        TrainingInsight insight = insight(
                new BigDecimal("30.0"), new BigDecimal("26.8"),
                50, 50,
                record("Dumbbell Bench Press", "22.5", DayOfWeekName.MONDAY, "20.00"),
                mostTrained("CORE", 4));
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your best single rose while your sessions stayed the same length.");
        assertThat(reading.body()).isEqualTo(
                "Your bench press best single rose 12% to 30 kg [1]. Your sessions took 50 min each [2]. "
                        + "Monday brought a Dumbbell Bench Press of 22.5 kg [3], up from 20 kg. "
                        + "Core work was part of 4 of 4 sessions [4].");
        assertThat(reading.citations()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("best single up, length down")
    void singleUpLengthDown() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("27"), 40, 55, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single rose in shorter sessions.");
    }

    @Test
    @DisplayName("best single up, length up")
    void singleUpLengthUp() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("27"), 60, 50, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single rose.");
    }

    @Test
    @DisplayName("best single up, no length fact at all: the general up rule applies")
    void singleUpNoLength() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("27"), null, null, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single rose.");
    }

    @Test
    @DisplayName("best single down, length down")
    void singleDownLengthDown() {
        TrainingInsight insight = insight(
                new BigDecimal("25"), new BigDecimal("30"), 40, 55, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single slipped.");
    }

    @Test
    @DisplayName("best single down, length unchanged: the general down rule applies")
    void singleDownOtherLength() {
        TrainingInsight insight = insight(
                new BigDecimal("25"), new BigDecimal("30"), 50, 50, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single slipped.");
    }

    @Test
    @DisplayName("best single unchanged, whatever the length is doing")
    void singleUnchanged() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("30"), 40, 55, null, null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your best single held.");
    }

    @Test
    @DisplayName("no best single or length fact, but a record: the record headline")
    void recordOnlyHeadline() {
        TrainingInsight insight = insight(
                null, null, null, null,
                record("Barbell Squat", "100", DayOfWeekName.FRIDAY, "90"), null);

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("You set a new personal best.");
    }

    @Test
    @DisplayName("nothing that matches a specific rule: the fact-count fallback headline")
    void fallbackHeadline() {
        TrainingInsight insight = insight(
                null, null, 45, null, null, mostTrained("BACK", 2));

        String headline = headlineOnly(insight);

        assertThat(headline).isEqualTo("Your training in two numbers.");
    }

    @Test
    @DisplayName("best single down sentence names the earlier value as 'down from'")
    void singleDownSentence() {
        TrainingInsight insight = insight(
                new BigDecimal("25"), new BigDecimal("30"), 40, 40, null, null);
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.body()).isEqualTo("Your bench press best single slipped 17% to 25 kg [1]. Your sessions took 40 min each [2].");
    }

    @Test
    @DisplayName("best single unchanged sentence says so instead of naming a direction")
    void singleUnchangedSentence() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("30"), 40, 40, null, null);
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.body())
                .isEqualTo("Your bench press best single held at 30 kg [1]. Your sessions took 40 min each [2].");
    }

    @Test
    @DisplayName("no length fact: the record is renumbered to fact 2 and cited as [2]")
    void factsNumberedDifferentlyWithNoLengthFact() {
        TrainingInsight insight = insight(
                new BigDecimal("30"), new BigDecimal("27"), null, null,
                record("Deadlift", "120", DayOfWeekName.FRIDAY, "110"), null);
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(facts).extracting(ReadingFact::key).containsExactly("BEST_SINGLE", "RECORD");
        assertThat(reading.body()).contains("[2]").doesNotContain("[3]");
    }

    @ParameterizedTest
    @DisplayName("an exercise name the validator would reject drops the record sentence but the reading still validates")
    @MethodSource("awkwardExerciseNames")
    void awkwardExerciseNameDropsTheRecordSentence(String exerciseName) {
        TrainingInsight insight = insight(
                new BigDecimal("30.0"), new BigDecimal("26.8"),
                50, 50,
                record(exerciseName, "22.5", DayOfWeekName.MONDAY, "20.00"),
                mostTrained("CORE", 4));
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your best single rose while your sessions stayed the same length.");
        assertThat(reading.body()).doesNotContain(exerciseName).doesNotContain("[3]");
        assertThat(validator.validate(reading.headline(), reading.body(), facts).valid()).isTrue();
    }

    static Stream<String> awkwardExerciseNames() {
        return Stream.of(
                "21s Barbell Curl",
                "Is this a press?",
                "No-Hands Squat",
                "Row - wide grip",
                "Barbell Curl 💪");
    }

    @ParameterizedTest
    @DisplayName("every combination the rules can produce validates cleanly and stays within length limits")
    @MethodSource("directionCombinations")
    void everyCombinationValidates(BigDecimal single, BigDecimal earlierSingle, Integer minutes,
                                   Integer earlierMinutes, boolean withRecord, boolean withMostTrained) {
        TrainingInsight insight = insight(single, earlierSingle, minutes, earlierMinutes,
                withRecord ? record("Barbell Row", "60", DayOfWeekName.TUESDAY, "55") : null,
                withMostTrained ? mostTrained("LEGS", 3) : null);
        List<ReadingFact> facts = factsBuilder.build(insight);
        if (facts.isEmpty()) {
            return;
        }

        Optional<RuleReadingWriter.RuleReading> reading = writer.write(insight, facts);

        assertThat(reading).isPresent();
        ReadingValidator.Result result = validator.validate(reading.get().headline(), reading.get().body(), facts);
        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(reading.get().headline().length()).isLessThanOrEqualTo(80);
        assertThat(reading.get().body().length()).isLessThanOrEqualTo(320);
    }

    static Stream<Arguments> directionCombinations() {
        BigDecimal up = new BigDecimal("30");
        BigDecimal upEarlier = new BigDecimal("27");
        BigDecimal down = new BigDecimal("25");
        BigDecimal downEarlier = new BigDecimal("30");
        BigDecimal same = new BigDecimal("30");
        return Stream.of(
                Arguments.of(up, upEarlier, 40, 55, false, false),
                Arguments.of(up, upEarlier, 60, 50, false, false),
                Arguments.of(up, upEarlier, 50, 50, false, false),
                Arguments.of(up, upEarlier, null, null, false, false),
                Arguments.of(down, downEarlier, 40, 55, false, false),
                Arguments.of(down, downEarlier, 50, 50, false, false),
                Arguments.of(same, same, 40, 55, false, false),
                Arguments.of(up, upEarlier, 50, 50, true, true),
                Arguments.of(down, downEarlier, 40, 55, true, false),
                Arguments.of(same, same, 40, 40, false, true),
                Arguments.of((BigDecimal) null, null, 45, null, true, true),
                Arguments.of((BigDecimal) null, null, 45, null, false, true),
                Arguments.of(up, upEarlier, null, null, true, false),
                Arguments.of(down, downEarlier, null, null, false, true));
    }

    @Test
    @DisplayName("a lift named in capitals is written in lower case, so it never reads as a status code")
    void liftNameInCapitalsIsWrittenPlainly() {
        TrainingInsight insight = insight(null, null, 40, 40, null, null).withBestSingles(List.of(
                new TrainingInsight.BestSingle("BARBELL_BENCH_PRESS", new BigDecimal("80"), new BigDecimal("84"), 5)));
        List<ReadingFact> facts = factsBuilder.build(insight);

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.body()).startsWith("Your barbell bench press best single rose 5% to 84 kg [1].")
                .doesNotContain("moved");
        assertThat(com.fitsync.recommendation.coach.guardrail.CoachVoice.RAW_STATUS_WORD.matcher(reading.body()).find())
                .isFalse();
        assertThat(validator.validate(reading.headline(), reading.body(), facts).valid()).isTrue();
    }

    @Test
    @DisplayName("facts with nothing usable to say produce no reading")
    void nothingToSayIsEmpty() {
        TrainingInsight insight = insight(null, null, null, null, null, mostTrained("UNKNOWN_GROUP", 2));
        List<ReadingFact> facts = factsBuilder.build(insight);

        Optional<RuleReadingWriter.RuleReading> reading = writer.write(insight, facts);

        assertThat(reading).isEmpty();
    }

    private String headlineOnly(TrainingInsight insight) {
        List<ReadingFact> facts = factsBuilder.build(insight);
        return writer.write(insight, facts).orElseThrow().headline();
    }

    private enum DayOfWeekName { MONDAY, TUESDAY, FRIDAY }

    private static TrainingInsight.RecordInfo record(String exerciseName, String weight, DayOfWeekName day,
                                                      String previousBest) {
        LocalDate achievedOn = switch (day) {
            case MONDAY -> LocalDate.parse("2026-09-21");
            case TUESDAY -> LocalDate.parse("2026-09-22");
            case FRIDAY -> LocalDate.parse("2026-09-25");
        };
        return new TrainingInsight.RecordInfo(exerciseName, new BigDecimal(weight), achievedOn,
                previousBest == null ? null : new BigDecimal(previousBest));
    }

    @Test
    @DisplayName("lifting with runs: the lifting sentences come first, then distance and pace")
    void liftingAndRuns() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                ReadingFactsBuilderTests.fullInsight(), runs("5.75", "5.50", 365, 370), null, null));

        assertThat(reading.headline()).isEqualTo("Your best single rose while your sessions stayed the same length.");
        assertThat(reading.body()).isEqualTo(
                "Your dumbbell bench press best single rose 12% to 30 kg [1]. Your sessions took 50 min each [2]. "
                        + "Your cardio sessions averaged 5.75 km [3], up from 5.5 km. "
                        + "The pace was 6:05 per km [4], 5 s faster than the week before.");
    }

    @Test
    @DisplayName("only runs: the headline follows the distance and the pace names its direction")
    void onlyRuns() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, runs("5.75", "5.50", 365, 370), null, null));

        assertThat(reading.headline()).isEqualTo("Your cardio went further.");
        assertThat(reading.body()).isEqualTo(
                "Your cardio sessions averaged 5.75 km [1], up from 5.5 km. "
                        + "The pace was 6:05 per km [2], 5 s faster than the week before.");
        assertThat(reading.citations()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("runs with no distance the week before: no comparison is made")
    void runsWithoutAnEarlierDistance() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, runs("5.75", null, 365, null), null, null));

        assertThat(reading.body()).startsWith("Your cardio sessions averaged 5.75 km [1].");
        assertThat(reading.body()).doesNotContain("up from", "down from");
    }

    @Test
    @DisplayName("shorter runs and a slower pace")
    void shorterAndSlower() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, runs("5.00", "5.50", 370, 365), null, null));

        assertThat(reading.headline()).isEqualTo("Your cardio covered less distance.");
        assertThat(reading.body()).isEqualTo(
                "Your cardio sessions averaged 5 km [1], down from 5.5 km. "
                        + "The pace was 6:10 per km [2], 5 s slower than the week before.");
    }

    @Test
    @DisplayName("runs at the same distance and pace as the week before")
    void unchangedRuns() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, runs("5.50", "5.50", 365, 365), null, null));

        assertThat(reading.headline()).isEqualTo("Your cardio held its distance.");
        assertThat(reading.body()).isEqualTo(
                "Your cardio sessions averaged 5.5 km [1], the same as the week before. "
                        + "The pace was 6:05 per km [2], the same as the week before.");
    }

    @Test
    @DisplayName("runs with no earlier week get the plain headline, because held would be a claim no fact makes")
    void runsWithoutAnEarlierWeek() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, runs("5.75", null, 365, null), null, null));

        assertThat(reading.headline()).isEqualTo("Your training in two numbers.");
        assertThat(reading.body()).isEqualTo(
                "Your cardio sessions averaged 5.75 km [1]. The pace was 6:05 per km [2].");
    }

    @Test
    @DisplayName("cardio without a distance, with mobility and a sport")
    void cardioLengthMobilityAndSport() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, ReadingFactsBuilderTests.cardio(null, null, null, null, 35, null),
                new TrainingInsight.MobilityInfo(1, 20, null, null),
                new TrainingInsight.SportInfo(1, 60, List.of(
                        new TrainingInsight.SportActivity("BADMINTON", "Badminton", 1, 60)))));

        assertThat(reading.headline()).isEqualTo("Your training in three numbers.");
        assertThat(reading.body()).isEqualTo(
                "Your cardio sessions took 35 min [1]. Mobility took 20 min [2]. Badminton took 60 min [3].");
    }

    @Test
    @DisplayName("cardio length that did not change headlines that it held its length")
    void unchangedCardioLength() {
        RuleReadingWriter.RuleReading reading = writeFor(ReadingFactsBuilderTests.withGroups(
                null, ReadingFactsBuilderTests.cardio(null, null, null, null, 35, 35),
                new TrainingInsight.MobilityInfo(1, 20, null, null), null));

        assertThat(reading.headline()).isEqualTo("Your cardio held its length.");
        assertThat(reading.body()).isEqualTo("Your cardio sessions took 35 min [1]. Mobility took 20 min [2].");
    }

    @Test
    @DisplayName("every reading written for the cardio cases passes the validator")
    void everyCardioReadingIsValid() {
        List<TrainingInsight> insights = List.of(
                ReadingFactsBuilderTests.withGroups(ReadingFactsBuilderTests.fullInsight(),
                        runs("5.75", "5.50", 365, 370), null, null),
                ReadingFactsBuilderTests.withGroups(ReadingFactsBuilderTests.fullInsight(),
                        ReadingFactsBuilderTests.cardio(null, null, null, null, 35, 40), null, null),
                ReadingFactsBuilderTests.withGroups(null, runs("5.00", "5.50", 370, 365), null, null),
                ReadingFactsBuilderTests.withGroups(null, runs("5.50", "5.50", 365, 365), null, null),
                ReadingFactsBuilderTests.withGroups(null, runs("5.75", null, 365, null), null, null));

        for (TrainingInsight insight : insights) {
            List<ReadingFact> facts = factsBuilder.build(insight);
            RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

            assertThat(validator.validate(reading.headline(), reading.body(), facts).valid())
                    .as(reading.headline() + " " + reading.body()).isTrue();
        }
    }

    private RuleReadingWriter.RuleReading writeFor(TrainingInsight insight) {
        return writer.write(insight, factsBuilder.build(insight)).orElseThrow();
    }

    private static TrainingInsight.CardioInfo runs(String distance, String earlierDistance, int pace,
                                                   Integer earlierPace) {
        return new TrainingInsight.CardioInfo(2, 2, new BigDecimal(distance),
                earlierDistance == null ? null : new BigDecimal(earlierDistance),
                BigDecimal.valueOf(pace), earlierPace == null ? null : BigDecimal.valueOf(earlierPace), 35, null);
    }

    private static TrainingInsight.MostTrainedInfo mostTrained(String muscleGroup, int sessions) {
        return new TrainingInsight.MostTrainedInfo(muscleGroup, sessions, List.of("Plank Hold"));
    }

    /** A lifting week whose main lift's best single went from {@code earlierSingle} to {@code single} kg. */
    private static TrainingInsight insight(BigDecimal single, BigDecimal earlierSingle, Integer minutes,
                                           Integer earlierMinutes, TrainingInsight.RecordInfo record,
                                           TrainingInsight.MostTrainedInfo mostTrained) {
        int sessionsInPeriod = mostTrained == null ? 4 : (mostTrained.muscleGroup().equals("CORE") ? 4
                : mostTrained.muscleGroup().equals("BACK") ? 3
                : mostTrained.muscleGroup().equals("LEGS") ? 5 : 3);
        return new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                sessionsInPeriod, 2,
                null, null,
                minutes, earlierMinutes,
                record, mostTrained, List.of())
                .withBestSingles(single == null ? List.of() : List.of(new TrainingInsight.BestSingle("Bench Press",
                        earlierSingle, single, earlierSingle == null ? null
                        : single.subtract(earlierSingle).multiply(BigDecimal.valueOf(100))
                                .divide(earlierSingle, 0, java.math.RoundingMode.HALF_UP).intValue())));
    }
}
