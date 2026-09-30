package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgressRuleReadingWriterTests {

    private final ReadingValidator validator = new ReadingValidator(new CoachGuardrail());
    private final ProgressFactsBuilder factsBuilder = new ProgressFactsBuilder();
    private final ProgressRuleReadingWriter writer = new ProgressRuleReadingWriter(validator);

    @Test
    @DisplayName("a full insight is read against the goal date, and the projected year is written when it differs")
    void fullInsight() {
        RuleReadingWriter.RuleReading reading =
                write(PageInsightFixtures.progress(), PageInsightFixtures.nutrition()).orElseThrow();

        assertThat(reading.headline()).isEqualTo("At this rate you reach 68 kg after your date.");
        assertThat(reading.body()).isEqualTo(
                "You weighed 71.1 kg on 28 Sept [1]. "
                        + "That is down 0.21 kg a week [2]. "
                        + "Your goal is 68 kg by 9 Dec [3], and this rate gets there on 10 Jan 2027. "
                        + "Calories came to 2,173 kcal a day [4].");
        assertThat(reading.citations()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("a projection on or before the goal date is reached before the date")
    void beforeYourDate() {
        ProgressInsight early = PageInsightFixtures.progress(new BigDecimal("-0.21"),
                LocalDate.parse("2026-12-09"), LocalDate.parse("2026-11-20"));
        assertThat(write(early, null).orElseThrow().headline())
                .isEqualTo("At this rate you reach 68 kg before your date.");
        assertThat(write(early, null).orElseThrow().body())
                .contains("this rate gets there on 20 Nov.");

        ProgressInsight onTheDay = PageInsightFixtures.progress(new BigDecimal("-0.21"),
                LocalDate.parse("2026-12-09"), LocalDate.parse("2026-12-09"));
        assertThat(write(onTheDay, null).orElseThrow().headline())
                .isEqualTo("At this rate you reach 68 kg before your date.");
    }

    @Test
    @DisplayName("a projection without a goal date names the goal alone, and the goal sentence has no date")
    void projectionWithoutGoalDate() {
        ProgressInsight insight = PageInsightFixtures.progress(new BigDecimal("-0.21"), null,
                LocalDate.parse("2026-11-20"));

        RuleReadingWriter.RuleReading reading = write(insight, null).orElseThrow();

        assertThat(reading.headline()).isEqualTo("At this rate you reach 68 kg.");
        assertThat(reading.body()).contains("Your goal is 68 kg [3], and this rate gets there on 20 Nov.");
    }

    @Test
    @DisplayName("a goal and a rate but no projection say the weight is moving away when it is")
    void movingAway() {
        ProgressInsight gaining = PageInsightFixtures.progress(new BigDecimal("0.4"),
                LocalDate.parse("2026-12-09"), null);
        RuleReadingWriter.RuleReading reading = write(gaining, null).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your weight is moving away from 68 kg.");
        assertThat(reading.body()).contains("That is up 0.4 kg a week [2].")
                .contains("Your goal is 68 kg by 9 Dec [3].");
    }

    @Test
    @DisplayName("a goal above the weight with the weight falling is moving away too")
    void movingAwayFromAHigherGoal() {
        ProgressInsight insight = new ProgressInsight("UTC", LocalDate.parse("2026-10-01"), new BigDecimal("60"),
                LocalDate.parse("2026-09-30"), 5, new BigDecimal("-0.3"), 28, new BigDecimal("65"), null, null);

        assertThat(write(insight, null).orElseThrow().headline()).isEqualTo("Your weight is moving away from 65 kg.");
    }

    @Test
    @DisplayName("a goal with a zero rate says the weight has held, and the rate sentence says it stayed the same")
    void heldWithAGoal() {
        ProgressInsight insight = PageInsightFixtures.progress(BigDecimal.ZERO, LocalDate.parse("2026-12-09"), null);

        RuleReadingWriter.RuleReading reading = write(insight, null).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your weight has held over four weeks.");
        assertThat(reading.body()).contains("It stayed the same over four weeks [2].");
    }

    @Test
    @DisplayName("a rate towards the goal without a projection falls back to the direction of the weight")
    void towardsTheGoalWithoutAProjection() {
        ProgressInsight insight = PageInsightFixtures.progress(new BigDecimal("-0.21"),
                LocalDate.parse("2026-12-09"), null);

        assertThat(write(insight, null).orElseThrow().headline()).isEqualTo("Your weight went down over four weeks.");
    }

    @Test
    @DisplayName("a rate without a goal names the direction, or that the weight held")
    void rateWithoutAGoal() {
        assertThat(write(PageInsightFixtures.progressWithoutGoal(new BigDecimal("-0.5")), null).orElseThrow().headline())
                .isEqualTo("Your weight went down over four weeks.");
        assertThat(write(PageInsightFixtures.progressWithoutGoal(new BigDecimal("0.5")), null).orElseThrow().headline())
                .isEqualTo("Your weight went up over four weeks.");
        RuleReadingWriter.RuleReading held =
                write(PageInsightFixtures.progressWithoutGoal(BigDecimal.ZERO), null).orElseThrow();
        assertThat(held.headline()).isEqualTo("Your weight held over four weeks.");
        assertThat(held.body()).isEqualTo("You weighed 71.1 kg on 28 Sept [1]. It stayed the same over four weeks [2].");
    }

    @Test
    @DisplayName("with only a weight the headline is the latest weight and the body is one sentence")
    void onlyAWeight() {
        RuleReadingWriter.RuleReading reading =
                write(PageInsightFixtures.progressWithoutGoal(null), null).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your latest weight.");
        assertThat(reading.body()).isEqualTo("You weighed 71.1 kg on 28 Sept [1].");
    }

    @Test
    @DisplayName("without a measuring date the weight sentence has no date")
    void weightWithoutADate() {
        ProgressInsight insight = new ProgressInsight("UTC", LocalDate.parse("2026-10-01"), new BigDecimal("71.1"),
                null, 0, null, null, null, null, null);

        assertThat(write(insight, null).orElseThrow().body()).isEqualTo("You weighed 71.1 kg [1].");
    }

    @Test
    @DisplayName("without any weight there is no rule reading")
    void noWeightNoReading() {
        assertThat(write(PageInsightFixtures.noWeight(), null)).isEmpty();
    }

    @Test
    @DisplayName("every text the writer returns passes the real validator against the same facts")
    void everyTextPassesTheValidator() {
        ProgressInsight[] insights = {
                PageInsightFixtures.progress(),
                PageInsightFixtures.progress(new BigDecimal("0.4"), LocalDate.parse("2026-12-09"), null),
                PageInsightFixtures.progress(BigDecimal.ZERO, LocalDate.parse("2026-12-09"), null),
                PageInsightFixtures.progress(new BigDecimal("-0.21"), null, LocalDate.parse("2026-11-20")),
                PageInsightFixtures.progressWithoutGoal(new BigDecimal("-0.5")),
                PageInsightFixtures.progressWithoutGoal(new BigDecimal("0.5")),
                PageInsightFixtures.progressWithoutGoal(BigDecimal.ZERO),
                PageInsightFixtures.progressWithoutGoal(null)};
        for (ProgressInsight insight : insights) {
            for (NutritionInsight nutrition : new NutritionInsight[] {null, PageInsightFixtures.nutrition()}) {
                List<ReadingFact> facts = factsBuilder.build(insight, nutrition);
                RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();
                assertThat(validator.validate(reading.headline(), reading.body(), facts).violations()).isEmpty();
            }
        }
    }

    private Optional<RuleReadingWriter.RuleReading> write(ProgressInsight insight, NutritionInsight nutrition) {
        return writer.write(insight, factsBuilder.build(insight, nutrition));
    }
}
