package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressFactsBuilderTests {

    private final ProgressFactsBuilder builder = new ProgressFactsBuilder();

    @Test
    @DisplayName("a full insight gives the four facts in order, with the year only on a date in another year")
    void fullInsightGivesFourFacts() {
        List<ReadingFact> facts = builder.build(PageInsightFixtures.progress(), PageInsightFixtures.nutrition());

        assertThat(facts).extracting(ReadingFact::key).containsExactly(
                "WEIGHT", "WEEKLY_RATE", "GOAL", "CALORIES_PER_DAY");

        ReadingFact weight = facts.get(0);
        assertThat(weight.label()).isEqualTo("Weight");
        assertThat(weight.value()).isEqualTo("71.1");
        assertThat(weight.unit()).isEqualTo("kg");
        assertThat(weight.note()).isEqualTo("28 Sept");
        assertThat(weight.detail()).isEqualTo("16 weigh-ins in eight weeks");

        ReadingFact rate = facts.get(1);
        assertThat(rate.label()).isEqualTo("Change a week");
        assertThat(rate.value()).isEqualTo("0.21");
        assertThat(rate.unit()).isEqualTo("kg");
        assertThat(rate.note()).isEqualTo("Down");
        assertThat(rate.detail()).isEqualTo("over the last 28 days");

        ReadingFact goal = facts.get(2);
        assertThat(goal.label()).isEqualTo("Goal");
        assertThat(goal.value()).isEqualTo("68");
        assertThat(goal.unit()).isEqualTo("kg");
        assertThat(goal.note()).isEqualTo("by 9 Dec");
        assertThat(goal.detail()).isEqualTo("10 Jan 2027 at this rate");

        assertThat(facts.get(3).value()).isEqualTo("2,173");
        assertThat(facts.get(3).number()).isEqualTo(4);
    }

    @Test
    @DisplayName("the goal date carries the year when it falls in another year than the reference date")
    void goalDateInAnotherYear() {
        ProgressInsight insight = PageInsightFixtures.progress(new BigDecimal("-0.21"),
                LocalDate.parse("2027-02-03"), LocalDate.parse("2026-12-20"));

        ReadingFact goal = builder.build(insight, null).get(2);

        assertThat(goal.note()).isEqualTo("by 3 Feb 2027");
        assertThat(goal.detail()).isEqualTo("20 Dec at this rate");
    }

    @Test
    @DisplayName("the weight is always written with one decimal, and one weigh-in is singular")
    void weightDecimalAndSingularEntry() {
        ProgressInsight insight = new ProgressInsight("UTC", LocalDate.parse("2026-10-01"), new BigDecimal("71"),
                LocalDate.parse("2026-09-30"), 1, null, null, null, null, null);

        ReadingFact weight = builder.build(insight, null).get(0);

        assertThat(weight.value()).isEqualTo("71.0");
        assertThat(weight.detail()).isEqualTo("1 weigh-in in eight weeks");
    }

    @Test
    @DisplayName("without entries or a measuring date the detail and the note are empty")
    void noEntriesNoDate() {
        ProgressInsight insight = new ProgressInsight("UTC", LocalDate.parse("2026-10-01"), new BigDecimal("71.1"),
                null, 0, null, null, null, null, null);

        List<ReadingFact> facts = builder.build(insight, null);

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).note()).isEmpty();
        assertThat(facts.get(0).detail()).isEmpty();
    }

    @Test
    @DisplayName("the rate is shown without its sign, Up for a gain and Unchanged for none, with trailing zeros dropped")
    void rateDirectionAndFormat() {
        ReadingFact gain = builder.build(PageInsightFixtures.progressWithoutGoal(new BigDecimal("0.30")), null).get(1);
        assertThat(gain.value()).isEqualTo("0.3");
        assertThat(gain.note()).isEqualTo("Up");

        ReadingFact none = builder.build(PageInsightFixtures.progressWithoutGoal(new BigDecimal("0.00")), null).get(1);
        assertThat(none.value()).isEqualTo("0");
        assertThat(none.note()).isEqualTo("Unchanged");

        ReadingFact tiny = builder.build(PageInsightFixtures.progressWithoutGoal(new BigDecimal("-0.004")), null).get(1);
        assertThat(tiny.value()).isEqualTo("0");
        assertThat(tiny.note()).isEqualTo("Unchanged");
    }

    @Test
    @DisplayName("a null rate leaves the rate fact out, and no goal weight leaves the goal fact out")
    void absentRateAndGoal() {
        List<ReadingFact> facts = builder.build(PageInsightFixtures.progressWithoutGoal(null), null);

        assertThat(facts).extracting(ReadingFact::key).containsExactly("WEIGHT");
    }

    @Test
    @DisplayName("a goal without a date or a projection has an empty note and an empty detail")
    void goalWithoutDates() {
        ProgressInsight insight = PageInsightFixtures.progress(new BigDecimal("-0.21"), null, null);

        ReadingFact goal = builder.build(insight, null).get(2);

        assertThat(goal.note()).isEmpty();
        assertThat(goal.detail()).isEmpty();
    }

    @Test
    @DisplayName("the calories fact is left out when nutrition has nothing or could not be read")
    void caloriesLeftOut() {
        assertThat(builder.build(PageInsightFixtures.progress(), PageInsightFixtures.noNutrition()))
                .extracting(ReadingFact::key).doesNotContain("CALORIES_PER_DAY");
        assertThat(builder.build(PageInsightFixtures.progress(), null))
                .extracting(ReadingFact::key).doesNotContain("CALORIES_PER_DAY");
    }

    @Test
    @DisplayName("dates use the short month names, with Sept for September")
    void shortDates() {
        assertThat(ProgressFactsBuilder.shortDate(LocalDate.parse("2026-01-05"), false)).isEqualTo("5 Jan");
        assertThat(ProgressFactsBuilder.shortDate(LocalDate.parse("2026-09-30"), false)).isEqualTo("30 Sept");
        assertThat(ProgressFactsBuilder.shortDate(LocalDate.parse("2026-12-31"), true)).isEqualTo("31 Dec 2026");
    }
}
