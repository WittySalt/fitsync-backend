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

    @Test
    @DisplayName("a period with only lifting produces exactly the four facts it always did")
    void onlyLiftingIsUnchanged() {
        List<ReadingFact> facts = builder.build(fullInsight());

        assertThat(facts).containsExactly(
                new ReadingFact(1, "VOLUME_PER_SESSION", "Weight moved per session", "1,923", "kg",
                        new BigDecimal("1923.00"), "Up 12%", "1,716 kg the week before"),
                new ReadingFact(2, "SESSION_LENGTH", "Session length", "50", "min",
                        BigDecimal.valueOf(50), "Unchanged", "50 min the week before"),
                new ReadingFact(3, "RECORD", "Dumbbell Bench Press record", "22.5", "kg",
                        new BigDecimal("22.50"), "Monday", "20 kg before"),
                new ReadingFact(4, "MOST_TRAINED", "Sessions with core work", "4", "of 4",
                        BigDecimal.valueOf(4), "Every one", "Plank Hold"));
    }

    @Test
    @DisplayName("distance and pace facts carry the change against the week before, and faster means fewer seconds")
    void distanceAndPaceFacts() {
        List<ReadingFact> facts = builder.build(withGroups(null,
                cardio(new BigDecimal("5.75"), new BigDecimal("5.50"), 365, 370, 35, 35), null, null));

        ReadingFact distance = facts.get(0);
        assertThat(distance.key()).isEqualTo("CARDIO_DISTANCE");
        assertThat(distance.label()).isEqualTo("Distance per cardio session");
        assertThat(distance.value()).isEqualTo("5.75");
        assertThat(distance.unit()).isEqualTo("km");
        assertThat(distance.note()).isEqualTo("Up 5%");
        assertThat(distance.detail()).isEqualTo("5.5 km the week before");

        ReadingFact pace = facts.get(1);
        assertThat(pace.key()).isEqualTo("CARDIO_PACE");
        assertThat(pace.label()).isEqualTo("Pace");
        assertThat(pace.value()).isEqualTo("6:05");
        assertThat(pace.unit()).isEqualTo("per km");
        assertThat(pace.note()).isEqualTo("5 s faster");
        assertThat(pace.detail()).isEqualTo("6:10 per km the week before");
    }

    @Test
    @DisplayName("a slower pace, a shorter distance and an unchanged pace are each named as such")
    void paceAndDistanceDirections() {
        List<ReadingFact> slower = builder.build(withGroups(null,
                cardio(new BigDecimal("5.00"), new BigDecimal("5.50"), 370, 365, null, null), null, null));
        List<ReadingFact> same = builder.build(withGroups(null,
                cardio(new BigDecimal("5.50"), new BigDecimal("5.50"), 365, 365, null, null), null, null));

        assertThat(slower.get(0).note()).isEqualTo("Down 9%");
        assertThat(slower.get(1).note()).isEqualTo("5 s slower");
        assertThat(slower.get(1).value()).isEqualTo("6:10");
        assertThat(same.get(0).note()).isEqualTo("Unchanged");
        assertThat(same.get(1).note()).isEqualTo("Unchanged");
    }

    @Test
    @DisplayName("without an earlier week the cardio facts have no note and no detail")
    void cardioWithoutEarlierValues() {
        List<ReadingFact> facts = builder.build(withGroups(null,
                cardio(new BigDecimal("5.75"), null, 365, null, 35, null), null, null));

        assertThat(facts).extracting(ReadingFact::note).containsExactly("", "");
        assertThat(facts).extracting(ReadingFact::detail).containsExactly("", "");
    }

    @Test
    @DisplayName("seconds are two digits in a pace")
    void paceSecondsAreTwoDigits() {
        List<ReadingFact> facts = builder.build(withGroups(null,
                cardio(new BigDecimal("5"), null, 363, null, null, null), null, null));

        assertThat(facts.get(1).value()).isEqualTo("6:03");
    }

    @Test
    @DisplayName("cardio with no distance gives one length fact")
    void cardioLengthWithoutDistance() {
        List<ReadingFact> facts = builder.build(withGroups(null,
                cardio(null, null, null, null, 35, 30), null, null));

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.key()).isEqualTo("CARDIO_LENGTH");
            assertThat(fact.label()).isEqualTo("Cardio session length");
            assertThat(fact.value()).isEqualTo("35");
            assertThat(fact.unit()).isEqualTo("min");
            assertThat(fact.note()).isEqualTo("Up 5 min");
            assertThat(fact.detail()).isEqualTo("30 min the week before");
        });
    }

    @Test
    @DisplayName("lifting and cardio with a distance give two lifting facts, then distance and pace")
    void liftingAndCardioWithDistance() {
        List<ReadingFact> facts = builder.build(withGroups(fullInsight(),
                cardio(new BigDecimal("5.75"), new BigDecimal("5.50"), 365, 370, 35, 35), null, null));

        assertThat(facts).extracting(ReadingFact::key).containsExactly(
                "VOLUME_PER_SESSION", "SESSION_LENGTH", "CARDIO_DISTANCE", "CARDIO_PACE");
        assertThat(facts).extracting(ReadingFact::number).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("lifting and cardio without a distance give the freed place to a third lifting fact")
    void liftingAndCardioWithoutDistance() {
        List<ReadingFact> facts = builder.build(withGroups(fullInsight(),
                cardio(null, null, null, null, 35, 35), null, null));

        assertThat(facts).extracting(ReadingFact::key).containsExactly(
                "VOLUME_PER_SESSION", "SESSION_LENGTH", "CARDIO_LENGTH", "RECORD");
        assertThat(facts).extracting(ReadingFact::number).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("only cardio, then mobility and the most played sport, in that order")
    void onlyCardioWithMobilityAndSport() {
        List<ReadingFact> facts = builder.build(withGroups(null,
                cardio(null, null, null, null, 35, null),
                new TrainingInsight.MobilityInfo(1, 20, 1, 15),
                new TrainingInsight.SportInfo(3, 100, List.of(
                        new TrainingInsight.SportActivity("RUNNING_CLUB", "Running club", 1, 40),
                        new TrainingInsight.SportActivity("BADMINTON", "Badminton", 2, 60)))));

        assertThat(facts).extracting(ReadingFact::key).containsExactly(
                "CARDIO_LENGTH", "MOBILITY_TIME", "SPORT_TIME");

        ReadingFact mobility = facts.get(1);
        assertThat(mobility.label()).isEqualTo("Mobility");
        assertThat(mobility.value()).isEqualTo("20");
        assertThat(mobility.unit()).isEqualTo("min");
        assertThat(mobility.note()).isEqualTo("1 session");
        assertThat(mobility.detail()).isEqualTo("15 min the week before");

        ReadingFact sport = facts.get(2);
        assertThat(sport.label()).isEqualTo("Badminton");
        assertThat(sport.value()).isEqualTo("60");
        assertThat(sport.unit()).isEqualTo("min");
        assertThat(sport.note()).isEqualTo("2 sessions");
        assertThat(sport.detail()).isEmpty();
    }

    @Test
    @DisplayName("mobility and sport fill the free places after lifting, and never push out a fourth fact")
    void mobilityAndSportAfterLifting() {
        TrainingInsight twoLifting = new TrainingInsight(
                "UTC", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                4, 2, new BigDecimal("1923.00"), null, 50, null, null, null, List.of(), null,
                new TrainingInsight.MobilityInfo(1, 20, null, null),
                new TrainingInsight.SportInfo(1, 60, List.of(
                        new TrainingInsight.SportActivity("BADMINTON", "Badminton", 1, 60))));

        assertThat(builder.build(twoLifting)).extracting(ReadingFact::key).containsExactly(
                "VOLUME_PER_SESSION", "SESSION_LENGTH", "MOBILITY_TIME", "SPORT_TIME");
        assertThat(builder.build(withGroups(fullInsight(), null,
                new TrainingInsight.MobilityInfo(1, 20, null, null), null)))
                .extracting(ReadingFact::key)
                .containsExactly("VOLUME_PER_SESSION", "SESSION_LENGTH", "RECORD", "MOST_TRAINED");
    }

    @Test
    @DisplayName("the new groups are read from workout-service JSON, with unknown properties ignored")
    void newGroupsAreReadFromJson() throws Exception {
        String json = "{\"period\":\"PREVIOUS_WEEK\",\"somethingNew\":1,"
                + "\"cardio\":{\"sessions\":2,\"distancePerSessionKm\":5.75,\"paceSecondsPerKm\":365,"
                + "\"earlierPaceSecondsPerKm\":370,\"minutesPerSession\":35,\"extra\":true},"
                + "\"mobility\":{\"sessions\":1,\"minutes\":20},"
                + "\"sport\":{\"sessions\":1,\"minutes\":60,\"activities\":"
                + "[{\"activity\":\"BADMINTON\",\"label\":\"Badminton\",\"sessions\":1,\"minutes\":60}]}}";

        TrainingInsight insight = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules().readValue(json, TrainingInsight.class);

        assertThat(insight.cardio().distancePerSessionKm()).isEqualByComparingTo("5.75");
        assertThat(insight.cardio().earlierDistancePerSessionKm()).isNull();
        assertThat(insight.mobility().minutes()).isEqualTo(20);
        assertThat(insight.sport().activities()).extracting(TrainingInsight.SportActivity::label)
                .containsExactly("Badminton");
        assertThat(builder.build(insight)).extracting(ReadingFact::key).containsExactly(
                "CARDIO_DISTANCE", "CARDIO_PACE", "MOBILITY_TIME", "SPORT_TIME");
    }

    static TrainingInsight.CardioInfo cardio(BigDecimal distance, BigDecimal earlierDistance, Integer paceSeconds,
                                             Integer earlierPaceSeconds, Integer minutes, Integer earlierMinutes) {
        return new TrainingInsight.CardioInfo(2, 2, distance, earlierDistance,
                paceSeconds == null ? null : BigDecimal.valueOf(paceSeconds),
                earlierPaceSeconds == null ? null : BigDecimal.valueOf(earlierPaceSeconds),
                minutes, earlierMinutes);
    }

    /** The given lifting insight (or an empty period) with the cardio, mobility and sport groups set. */
    static TrainingInsight withGroups(TrainingInsight base, TrainingInsight.CardioInfo cardio,
                                      TrainingInsight.MobilityInfo mobility, TrainingInsight.SportInfo sport) {
        TrainingInsight from = base != null ? base : new TrainingInsight(
                "Asia/Singapore", LocalDate.parse("2026-09-28"), "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"),
                2, 0, null, null, null, null, null, null, List.of());
        return new TrainingInsight(from.timezone(), from.referenceDate(), from.period(), from.periodStart(),
                from.periodEnd(), from.sessions(), from.liftingSessions(), from.volumePerSessionKg(),
                from.earlierVolumePerSessionKg(), from.minutesPerLiftingSession(),
                from.earlierMinutesPerLiftingSession(), from.personalRecord(), from.mostTrained(), from.lifts(),
                cardio, mobility, sport);
    }

    static TrainingInsight fullInsight() {
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
