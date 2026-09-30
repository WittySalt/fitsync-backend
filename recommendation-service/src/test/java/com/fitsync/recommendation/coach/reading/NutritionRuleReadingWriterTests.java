package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fitsync.recommendation.coach.guardrail.CoachGuardrail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NutritionRuleReadingWriterTests {

    private final ReadingValidator validator = new ReadingValidator(new CoachGuardrail());
    private final NutritionFactsBuilder factsBuilder = new NutritionFactsBuilder();
    private final NutritionRuleReadingWriter writer = new NutritionRuleReadingWriter(validator);

    @Test
    @DisplayName("a full week is read as lunch and dinner heavy, with every sentence cited")
    void fullWeek() {
        RuleReadingWriter.RuleReading reading = write(PageInsightFixtures.nutrition()).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Most of your protein came at lunch and dinner.");
        assertThat(reading.body()).isEqualTo(
                "You ate 163.8 g of protein a day [1], up from 162.2 g. "
                        + "Breakfast gave 30.3 g of it [2], and lunch and dinner gave 109.9 g [3]. "
                        + "Calories came to 2,173 kcal a day [4].");
        assertThat(reading.citations()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("a fall reads down from, and no change reads the same as the week before")
    void fallAndNoChange() {
        NutritionInsight fall = PageInsightFixtures.nutrition(new BigDecimal("150"), new BigDecimal("156"),
                null, null, List.of());
        assertThat(write(fall).orElseThrow().body()).isEqualTo(
                "You ate 150 g of protein a day [1], down from 156 g.");

        NutritionInsight same = PageInsightFixtures.nutrition(new BigDecimal("150"), new BigDecimal("150.0"),
                null, null, List.of());
        assertThat(write(same).orElseThrow().body()).isEqualTo(
                "You ate 150 g of protein a day [1], the same as the week before.");
    }

    @Test
    @DisplayName("without an earlier week there is no comparison")
    void noComparison() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("150"), null, null, null, List.of());

        RuleReadingWriter.RuleReading reading = write(insight).orElseThrow();

        assertThat(reading.body()).isEqualTo("You ate 150 g of protein a day [1].");
        assertThat(reading.headline()).isEqualTo("Your protein for the week.");
    }

    @Test
    @DisplayName("a breakfast share of 35 percent or more says the protein started early")
    void earlyHeadline() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("100"), new BigDecimal("100"),
                null, null, PageInsightFixtures.meals("40", "30", "20", "10"));

        assertThat(write(insight).orElseThrow().headline()).isEqualTo("Your protein started early in the day.");
    }

    @Test
    @DisplayName("shares below both thresholds say the protein was spread across the day")
    void spreadHeadline() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("100"), new BigDecimal("100"),
                null, null, PageInsightFixtures.meals("25", "30", "20", "25"));

        assertThat(write(insight).orElseThrow().headline()).isEqualTo("Your protein was spread across the day.");
    }

    @Test
    @DisplayName("a lunch and dinner share of exactly 60 percent counts as most of it")
    void sixtyPercentIsTheThreshold() {
        NutritionInsight insight = PageInsightFixtures.nutrition(new BigDecimal("100"), null, null, null,
                PageInsightFixtures.meals("20", "30", "30", "20"));

        assertThat(write(insight).orElseThrow().headline()).isEqualTo("Most of your protein came at lunch and dinner.");
    }

    @Test
    @DisplayName("one half of the meal sentence stands alone when the other fact is missing")
    void oneMealHalf() {
        NutritionInsight breakfastOnly = PageInsightFixtures.nutrition(new BigDecimal("100"), null, null, null,
                List.of(new NutritionInsight.MealProtein("BREAKFAST", "Breakfast", new BigDecimal("20"))));
        assertThat(write(breakfastOnly).orElseThrow().body())
                .isEqualTo("You ate 100 g of protein a day [1]. Breakfast gave 20 g of it [2].");

        NutritionInsight lunchOnly = PageInsightFixtures.nutrition(new BigDecimal("100"), null, null, null,
                List.of(new NutritionInsight.MealProtein("LUNCH", "Lunch", new BigDecimal("50"))));
        assertThat(write(lunchOnly).orElseThrow().body())
                .isEqualTo("You ate 100 g of protein a day [1]. Lunch and dinner gave 50 g of it [2].");
    }

    @Test
    @DisplayName("calories alone are read on their own, under a headline about calories")
    void caloriesOnly() {
        NutritionInsight insight = PageInsightFixtures.nutrition(null, null, new BigDecimal("2173"), null, List.of());

        RuleReadingWriter.RuleReading reading = write(insight).orElseThrow();

        assertThat(reading.headline()).isEqualTo("Your calories for the week.");
        assertThat(reading.body()).isEqualTo("Calories came to 2,173 kcal a day [1].");
    }

    @Test
    @DisplayName("a week with no facts has no rule reading")
    void noFactsNoReading() {
        assertThat(write(PageInsightFixtures.noNutrition())).isEmpty();
    }

    @Test
    @DisplayName("the calories sentence survives the calorie guardrail, which reads the comma as part of the number")
    void thousandsCommaIsNotMistakenForALowFigure() {
        NutritionInsight insight = PageInsightFixtures.nutrition(null, null, new BigDecimal("1873"), null, List.of());

        assertThat(write(insight)).isPresent();
    }

    @Test
    @DisplayName("every text the writer returns passes the real validator against the same facts")
    void everyTextPassesTheValidator() {
        NutritionInsight[] insights = {
                PageInsightFixtures.nutrition(),
                PageInsightFixtures.nutrition(new BigDecimal("150"), new BigDecimal("156"), new BigDecimal("2000"),
                        new BigDecimal("2000"), PageInsightFixtures.meals("40", "30", "20", "10")),
                PageInsightFixtures.nutrition(new BigDecimal("150"), null, null, null, List.of())};
        for (NutritionInsight insight : insights) {
            List<ReadingFact> facts = factsBuilder.build(insight);
            RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();
            assertThat(validator.validate(reading.headline(), reading.body(), facts).violations()).isEmpty();
        }
    }

    @Test
    @DisplayName("a sentence that fails validation is dropped from the end, and the ones before it are kept")
    void failingSentenceIsDroppedFromTheEnd() {
        NutritionInsight insight = PageInsightFixtures.nutrition();
        List<ReadingFact> facts = new java.util.ArrayList<>(factsBuilder.build(insight).subList(0, 3));
        // A no-break space is outside the printable range the validator accepts, so the calories
        // sentence, which quotes this value, fails on its own.
        facts.add(new ReadingFact(4, "CALORIES_PER_DAY", "Calories a day", "2 173", "kcal", null, "", ""));

        RuleReadingWriter.RuleReading reading = writer.write(insight, facts).orElseThrow();

        assertThat(reading.body()).isEqualTo(
                "You ate 163.8 g of protein a day [1], up from 162.2 g. "
                        + "Breakfast gave 30.3 g of it [2], and lunch and dinner gave 109.9 g [3].");
    }


    private Optional<RuleReadingWriter.RuleReading> write(NutritionInsight insight) {
        return writer.write(insight, factsBuilder.build(insight));
    }
}
