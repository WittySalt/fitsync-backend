package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure fact-building over a {@link TrainingInsight}: no model, no I/O. A fact is included only when
 * its upstream data is present, and included facts are numbered consecutively from one.
 */
class ReadingFactsBuilderTests {

    private final ReadingFactsBuilder builder = new ReadingFactsBuilder();

    @Test
    @DisplayName("the worked example produces all four facts, in order, with the documented formatting")
    void fullExampleProducesAllFourFacts() {
        TrainingInsight insight = fullInsight();

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).hasSize(4);
        assertThat(facts).extracting(ReadingFact::number).containsExactly(1, 2, 3, 4);

        ReadingFact volume = facts.get(0);
        assertThat(volume.key()).isEqualTo("VOLUME_PER_SESSION");
        assertThat(volume.label()).isEqualTo("Weight moved per session");
        assertThat(volume.value()).isEqualTo("1,923");
        assertThat(volume.unit()).isEqualTo("kg");
        assertThat(volume.numericValue()).isEqualByComparingTo("1923.00");
        assertThat(volume.note()).isEqualTo("Up 12%");
        assertThat(volume.detail()).isEqualTo("1,716 kg the week before");

        ReadingFact length = facts.get(1);
        assertThat(length.key()).isEqualTo("SESSION_LENGTH");
        assertThat(length.label()).isEqualTo("Session length");
        assertThat(length.value()).isEqualTo("50");
        assertThat(length.unit()).isEqualTo("min");
        assertThat(length.note()).isEqualTo("Unchanged");
        assertThat(length.detail()).isEqualTo("50 min the week before");

        ReadingFact record = facts.get(2);
        assertThat(record.key()).isEqualTo("RECORD");
        assertThat(record.label()).isEqualTo("Dumbbell Bench Press record");
        assertThat(record.value()).isEqualTo("22.5");
        assertThat(record.unit()).isEqualTo("kg");
        assertThat(record.note()).isEqualTo("Monday");
        assertThat(record.detail()).isEqualTo("20 kg before");

        ReadingFact mostTrained = facts.get(3);
        assertThat(mostTrained.key()).isEqualTo("MOST_TRAINED");
        assertThat(mostTrained.label()).isEqualTo("Sessions with core work");
        assertThat(mostTrained.value()).isEqualTo("4");
        assertThat(mostTrained.unit()).isEqualTo("of 4");
        assertThat(mostTrained.note()).isEqualTo("Every one");
        assertThat(mostTrained.detail()).isEqualTo("Plank Hold");
    }

    @Test
    @DisplayName("a fact is omitted when its data is missing, and numbering stays consecutive")
    void missingDataOmitsAFactAndRenumbers() {
        TrainingInsight insight = new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new BigDecimal("1923.00"), null,
                null, null,
                null,
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of("Plank Hold")),
                List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).key()).isEqualTo("VOLUME_PER_SESSION");
        assertThat(facts.get(0).number()).isEqualTo(1);
        assertThat(facts.get(0).note()).isEmpty();
        assertThat(facts.get(0).detail()).isEmpty();
        assertThat(facts.get(1).key()).isEqualTo("MOST_TRAINED");
        assertThat(facts.get(1).number()).isEqualTo(2);
    }

    @Test
    @DisplayName("no upstream data at all produces no facts")
    void noDataProducesNoFacts() {
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "NONE", null, null,
                0, 0, null, null, null, null, null, null, List.of());

        assertThat(builder.build(insight)).isEmpty();
    }

    @Test
    @DisplayName("an exercise name over eighty characters is cut, not rejected")
    void longExerciseNameIsCut() {
        String longName = "A".repeat(90);
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2, null, null, null, null,
                new TrainingInsight.RecordInfo(longName, new BigDecimal("22.50"), LocalDate.parse("2026-09-21"), null),
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of(longName)),
                List.of());

        List<ReadingFact> facts = builder.build(insight);

        ReadingFact record = facts.get(0);
        assertThat(record.label()).hasSize(80 + " record".length());
        assertThat(record.label()).startsWith("A".repeat(80));

        ReadingFact mostTrained = facts.get(1);
        assertThat(mostTrained.detail()).hasSize(80);
    }

    @Test
    @DisplayName("an unknown muscle group code is rendered in lower case with underscores turned to spaces")
    void unknownMuscleGroupCodeFallsBackToItsOwnText() {
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2, null, null, null, null, null,
                new TrainingInsight.MostTrainedInfo("HIP_FLEXORS", 4, List.of()),
                List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts).singleElement()
                .extracting(ReadingFact::label)
                .isEqualTo("Sessions with hip flexors work");
    }

    @Test
    @DisplayName("a record weight that is a whole number drops its trailing decimal")
    void wholeNumberRecordWeightHasNoTrailingDecimal() {
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2, null, null, null, null,
                new TrainingInsight.RecordInfo("Barbell Squat", new BigDecimal("20.00"),
                        LocalDate.parse("2026-09-21"), new BigDecimal("18.00")),
                null, List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts.get(0).value()).isEqualTo("20");
        assertThat(facts.get(0).detail()).isEqualTo("18 kg before");
    }

    @Test
    @DisplayName("a volume decrease reports a rounded percentage down, and a shorter session reports minutes down")
    void decreasesAreReportedAsDown() {
        TrainingInsight insight = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new BigDecimal("1500.00"), new BigDecimal("2000.00"),
                40, 50,
                null, null, List.of());

        List<ReadingFact> facts = builder.build(insight);

        assertThat(facts.get(0).note()).isEqualTo("Down 25%");
        assertThat(facts.get(1).note()).isEqualTo("Down 10 min");
    }

    private static TrainingInsight fullInsight() {
        return new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2,
                new BigDecimal("1923.00"), new BigDecimal("1716.00"),
                50, 50,
                new TrainingInsight.RecordInfo("Dumbbell Bench Press", new BigDecimal("22.50"),
                        LocalDate.parse("2026-09-21"), new BigDecimal("20.00")),
                new TrainingInsight.MostTrainedInfo("CORE", 4, List.of("Plank Hold")),
                List.of(new TrainingInsight.LiftProgress("Dumbbell Bench Press",
                        new BigDecimal("20.00"), LocalDate.parse("2026-09-16"),
                        new BigDecimal("22.50"), LocalDate.parse("2026-09-25"))));
    }
}
